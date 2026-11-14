$ErrorActionPreference='Stop'
$base='http://localhost:9090'
function Req($method,$path,$token,$body){$p=@{Uri="$base$path";Method=$method;SkipHttpErrorCheck=$true;TimeoutSec=15};if($token){$p.Headers=@{Authorization="Bearer $token"}};if($null -ne $body){$p.ContentType='application/json';$p.Body=$body|ConvertTo-Json};$r=Invoke-WebRequest @p;$d=$null;try{$d=$r.Content|ConvertFrom-Json}catch{$d=$r.Content};[pscustomobject]@{status=[int]$r.StatusCode;data=$d}}
function Check($name,$condition){if(!$condition){throw "FAILED $name"};Write-Output "PASS $name"}
$suffix=[guid]::NewGuid().ToString('N').Substring(0,10);$password=[guid]::NewGuid().ToString('N');$userA="reserve_a_$suffix";$userB="reserve_b_$suffix"
Check 'register A' ((Req POST '/api/user/register' $null @{username=$userA;password=$password;role='USER'}).status -eq 200)
Check 'register B' ((Req POST '/api/user/register' $null @{username=$userB;password=$password;role='USER'}).status -eq 200)
$tokenA=(Req POST '/api/user/login' $null @{username=$userA;password=$password}).data
$tokenB=(Req POST '/api/user/login' $null @{username=$userB;password=$password}).data
Check 'login' ($tokenA -and $tokenB)
$shows=(Req GET '/api/showtimes' $tokenA $null).data;$show=$shows|Where-Object {$_.availableSeats -ge 50}|Select-Object -First 1
Check 'showtime available' ($show.id -gt 0)
$path='/api/showtimes/reservations';$id=$show.id
Check 'invalid showtime' ((Req POST $path $tokenA @{showtimeId=999999999;quantity=1}).status -eq 404)
Check 'zero quantity' ((Req POST $path $tokenA @{showtimeId=$id;quantity=0}).status -eq 400)
Check 'negative quantity' ((Req POST $path $tokenA @{showtimeId=$id;quantity=-1}).status -eq 400)
Check 'insufficient quantity' ((Req POST $path $tokenA @{showtimeId=$id;quantity=($show.availableSeats+1)}).status -eq 409)
$res=Req POST $path $tokenA @{showtimeId=$id;quantity=2}
Check 'create active hold' ($res.status -eq 201 -and $res.data.status -eq 'ACTIVE')
$rid=$res.data.reservationId
Check 'retrieve owner hold' ((Req GET "$path/$rid" $tokenA $null).data.quantity -eq 2)
Check 'other user cannot read' ((Req GET "$path/$rid" $tokenB $null).status -eq 403)
Check 'permanent seats unchanged before confirm' (((Req GET '/api/showtimes' $tokenA $null).data|Where-Object id -eq $id|Select-Object -First 1).availableSeats -eq $show.availableSeats)
$cancel=Req POST "$path/$rid/cancel" $tokenA $null
Check 'cancel releases hold' ($cancel.status -eq 200 -and $cancel.data.status -eq 'CANCELLED')
Check 'cancelled cannot confirm' ((Req POST "$path/$rid/confirm" $tokenA $null).status -eq 409)
$exp=Req POST $path $tokenA @{showtimeId=$id;quantity=1}
Check 'expiry hold created' ($exp.status -eq 201)
Start-Sleep -Seconds 4
Check 'expiry status' ((Req GET "$path/$($exp.data.reservationId)" $tokenA $null).data.status -eq 'EXPIRED')
Check 'expired cannot confirm' ((Req POST "$path/$($exp.data.reservationId)/confirm" $tokenA $null).status -eq 410)
# Two requests execute concurrently against the same inventory snapshot.
$holdQuantity=[int]($show.availableSeats-3)
$jobs=@()
foreach($pair in @(@($tokenA,$holdQuantity),@($tokenB,6))){$jobs+=Start-ThreadJob -ArgumentList $base,$path,$id,$pair[0],$pair[1] -ScriptBlock {param($base,$path,$id,$token,$quantity);$r=Invoke-WebRequest "$base$path" -Method Post -ContentType 'application/json' -Headers @{Authorization="Bearer $token"} -Body (@{showtimeId=$id;quantity=$quantity}|ConvertTo-Json) -SkipHttpErrorCheck;[pscustomobject]@{status=[int]$r.StatusCode;data=$(try{$r.Content|ConvertFrom-Json}catch{$r.Content})} }}
$jobs|Wait-Job|Out-Null;$out=@($jobs|Receive-Job);$jobs|Remove-Job
Check 'concurrent reservations do not overhold' (@($out|Where-Object status -eq 201).Count -eq 1 -and @($out|Where-Object status -eq 409).Count -eq 1)
$winner=$out|Where-Object status -eq 201|Select-Object -First 1;$winnerToken=if($winner.data.quantity -eq $holdQuantity){$tokenA}else{$tokenB}
Check 'winning hold cancelled' ((Req POST "$path/$($winner.data.reservationId)/cancel" $winnerToken $null).status -eq 200)
$valid=Req POST $path $tokenA @{showtimeId=$id;quantity=2}
Check 'hold after release' ($valid.status -eq 201)
$confirm=Req POST "$path/$($valid.data.reservationId)/confirm" $tokenA $null
Check 'confirm once' ($confirm.status -eq 200 -and $confirm.data.status -eq 'CONFIRMED')
Check 'confirm twice rejected' ((Req POST "$path/$($valid.data.reservationId)/confirm" $tokenA $null).status -eq 409)
Check 'cancel after confirm rejected' ((Req POST "$path/$($valid.data.reservationId)/cancel" $tokenA $null).status -eq 409)
$after=(Req GET '/api/showtimes' $tokenA $null).data|Where-Object id -eq $id|Select-Object -First 1
Check 'MySQL decremented by confirmed quantity' ($after.availableSeats -eq $show.availableSeats-2)
$booking=Req POST '/api/bookings' $tokenA @{showtimeId=$id;seatsBooked=1}
Check 'booking flow reserves and confirms' ($booking.status -eq 200 -and $booking.data.id -gt 0)
$mine=Req GET '/api/bookings/mine' $tokenA $null
Check 'booking retrieval' ($mine.status -eq 200 -and $booking.data.id -in @($mine.data.id))
$final=(Req GET '/api/showtimes' $tokenA $null).data|Where-Object id -eq $id|Select-Object -First 1
Check 'booking decremented once' ($final.availableSeats -eq $show.availableSeats-3)


