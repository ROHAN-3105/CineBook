import axios from 'axios';
export const api=axios.create({baseURL:import.meta.env.VITE_API_BASE_URL || 'http://localhost:9090'});
api.interceptors.request.use(c=>{const t=localStorage.getItem('cinebook_token'); if(t)c.headers.Authorization=`Bearer ${t}`;return c});
api.interceptors.response.use(r=>r,e=>{if(e.response?.status===401 && localStorage.getItem('cinebook_token')){localStorage.removeItem('cinebook_token');window.dispatchEvent(new Event('auth-change'))} return Promise.reject(new Error(e.response?.status===401?'Session expired or invalid credentials.':e.response?.status===403?'Access denied.':typeof e.response?.data==='string'?e.response.data:e.response?'Service error. Please try again.':'Cannot reach the gateway. Check that the backend is running.'))});
export type Movie={id:number;title:string;description:string;duration:number;rating:number};
export type Show={id:number;movieId:number;showTime:string;availableSeats:number};
export function claims(token:string):{sub:string;role:string;exp:number}{const s=token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/');return JSON.parse(decodeURIComponent(Array.from(atob(s),c=>'%'+c.charCodeAt(0).toString(16).padStart(2,'0')).join('')))}
