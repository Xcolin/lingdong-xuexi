export function dashboardEntryAllowed(feature:boolean,permissions:readonly string[],permission:string):boolean{return feature&&permissions.includes(permission);}
export function dashboardSessionCurrent(revision:number,currentRevision:number,loadedToken:string|undefined,currentToken:string|undefined):boolean{return revision===currentRevision&&!!loadedToken&&loadedToken===currentToken;}
export function dashboardNavigationAllowed(enabled:boolean,loadedToken:string,currentToken:string|undefined):boolean{return enabled&&!!loadedToken&&loadedToken===currentToken;}
