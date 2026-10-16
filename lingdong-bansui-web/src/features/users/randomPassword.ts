/** Generate a fresh eight-character password with letters and digits using Web Crypto. */
export function randomPassword():string {
 const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
 function pick(size:number){const data=new Uint32Array(1);const limit=Math.floor(0x100000000/size)*size;do{crypto.getRandomValues(data);}while(data[0]>=limit);return data[0]%size;}
 const chars=[alphabet[pick(52)],alphabet[52+pick(10)],...Array.from({length:6},()=>alphabet[pick(alphabet.length)])];
 for(let i=chars.length-1;i>0;i--){const j=pick(i+1);[chars[i],chars[j]]=[chars[j],chars[i]];}
 return chars.join('');
}
