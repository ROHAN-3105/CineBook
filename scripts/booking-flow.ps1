$ErrorActionPreference='Stop'
$base='http://localhost:9090'
function Request($method,$path,$token,$body) {
  $p=@{Uri="$base$path";Method=$method;SkipHttpErrorCheck=$true;TimeoutSec=20}
  if($token){$p.Headers=@{Authorization="Bearer $token"}}
  if($null -ne $body){$p.ContentType='application/json';$p.Body=($body|ConvertTo-Json)}
  $r=Invoke-WebRequest @p
  $data=$null
  try{$data=$r.Content|ConvertFrom-Json}catch{$data=$r.Content}
  return [pscustomobject]@{status=[int]$r.StatusCode;data=$data}
}
function Check($name,$ok){if(!$ok){throw "FAILED $name"};Write-Output "PASS $name"}
$suffix=[guid]::NewGuid().ToString('N').Substring(0,10)
$pass=[guid]::NewGuid().ToString('N')
$a="booking_a_$suffix";$b="booking_b_$suffix"
Check 'register A' ((Request POST '/api/user/register' $null @{username=$a;password=$pass;role='USER'}).status -eq 200)
Check 'register B' ((Request POST '/api/user/register' $null @{username=$b;password=$pass;role='USER'}).status -eq 200)
$ta=(Request POST '/api/user/login' $null @{username=$a;password=$pass}).data
$tb=(Request POST '/api/user/login' $null @{username=$b;password=$pass}).data
$me=(Request GET '/api/user/me' $ta $null)
Check 'current user id' ($me.status -eq 200 -and $me.data.username -eq $a -and $me.data.id -gt 0 -and !$me.data.password)
Check 'unauthenticated booking denied' ((Request POST '/api/bookings' $null @{showtimeId=3;seatsBooked=1}).status -eq 401)
Check 'supplied user id rejected' ((Request POST '/api/bookings' $ta @{userId=999;showtimeId=3;seatsBooked=1}).status -eq 400)
Check 'zero seats rejected' ((Request POST '/api/bookings' $ta @{showtimeId=3;seatsBooked=0}).status -eq 400)
Check 'negative seats rejected' ((Request POST '/api/bookings' $ta @{showtimeId=3;seatsBooked=-1}).status -eq 400)
$shows=(Request GET '/api/showtimes' $ta $null)
Check 'showtimes available' ($shows.status -eq 200 -and $shows.data.Count -gt 0)
$show=$shows.data | Where-Object { $_.availableSeats -gt 2 } | Select-Object -First 1
Check 'insufficient seats rejected' ((Request POST '/api/bookings' $ta @{showtimeId=$show.id;seatsBooked=($show.availableSeats+1)}).status -eq 409)
$before=$show.availableSeats
$created=Request POST '/api/bookings' $ta @{showtimeId=$show.id;seatsBooked=1}
Check 'booking created for token owner' ($created.status -eq 200 -and $created.data.userId -eq $me.data.id -and $created.data.id -gt 0)
$mineA=Request GET '/api/bookings/mine' $ta $null
$mineB=Request GET '/api/bookings/mine' $tb $null
Check 'owner sees own booking' ($mineA.status -eq 200 -and $created.data.id -in @($mineA.data.id))
Check 'other user sees none' ($mineB.status -eq 200 -and $created.data.id -notin @($mineB.data.id))
Check 'path lookup rejects other user' ((Request GET "/api/bookings/$($me.data.id)" $tb $null).status -eq 403)
$after=(Request GET '/api/showtimes' $ta $null).data | Where-Object id -eq $show.id | Select-Object -First 1
Check 'seat availability decremented once' ($after.availableSeats -eq $before-1)

