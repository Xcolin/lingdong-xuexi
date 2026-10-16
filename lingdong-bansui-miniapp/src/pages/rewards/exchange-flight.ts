export function createExchangeFlight(getToken:()=>string|undefined) {
 let running=false;
 return async function<T>(confirmed:boolean,request:()=>Promise<T>,apply:(result:T)=>void):Promise<boolean> {
  const token=getToken();if(!confirmed||running||!token)return false;
  running=true;
  try {const result=await request();if(getToken()!==token)return false;apply(result);return true;}
  finally {running=false;}
 };
}
