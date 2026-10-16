// Local invalidation only; no account or credential data is broadcast.
const listeners=new Set<()=>void>();
export function notifyTeacherProfileChanged(){for(const listener of listeners)listener();}
export function subscribeTeacherProfileChanged(listener:()=>void){listeners.add(listener);return()=>{listeners.delete(listener);};}
