import {ref,type Ref} from 'vue';

export interface RelationshipScope {token:string;organizationId:string}
export interface RelationshipPage<T> {items:T[];total:number;page:number;pageSize:number}

/** Each visible section owns its cursor and errors; an old identity/scope never refills it. */
export function useRelationshipSection<T>(read:(scope:RelationshipScope,page:number)=>Promise<RelationshipPage<T>>,
  key:(item:T)=>string,getScope:()=>RelationshipScope|null){
  const rows=ref([]) as Ref<T[]>,total=ref(0),page=ref(1),hasMore=ref(false),busy=ref(false),error=ref(''),loaded=ref(false);
  let revision=0;
  function clear(){revision++;rows.value=[];total.value=0;page.value=1;hasMore.value=false;busy.value=false;error.value='';loaded.value=false;}
  async function load(append:boolean,retry=false){
    if(append&&(busy.value||!hasMore.value||(error.value&&!retry)))return;
    if(!append)clear();
    const scope=getScope();if(!scope)return;
    const current=revision,target=append?page.value+1:1;
    const valid=()=>{const active=getScope();return current===revision&&active?.token===scope.token&&active?.organizationId===scope.organizationId;};
    busy.value=true;error.value='';
    try{const result=await read(scope,target);if(!valid())return;
      const ids=new Set(append?rows.value.map(key):[]);
      const added=result.items.filter(item=>{const id=key(item);if(ids.has(id))return false;ids.add(id);return true;});
      rows.value=append?[...rows.value,...added]:added;total.value=result.total;page.value=result.page;
      hasMore.value=added.length>0&&result.page*result.pageSize<result.total;loaded.value=true;
    }catch(cause){if(valid())error.value=cause instanceof Error?cause.message:'加载失败，请重试';}
    finally{if(current===revision)busy.value=false;}
  }
  return {rows,total,page,hasMore,busy,error,loaded,clear,first:()=>load(false),more:(retry=false)=>load(true,retry)};
}
