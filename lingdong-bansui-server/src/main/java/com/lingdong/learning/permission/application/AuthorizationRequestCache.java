package com.lingdong.learning.permission.application;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import java.util.*;
import java.util.function.Supplier;
/** Request-only snapshot; never retains grants across HTTP requests or across users. */
final class AuthorizationRequestCache {
 private AuthorizationRequestCache(){}
 @SuppressWarnings("unchecked") static <T>T get(String key,Supplier<T> query){
  RequestAttributes request=RequestContextHolder.getRequestAttributes();
  if(request==null)return query.get();
  String attribute=AuthorizationRequestCache.class.getName();
  Map<String,Object> cache=(Map<String,Object>)request.getAttribute(attribute,RequestAttributes.SCOPE_REQUEST);
  if(cache==null){cache=new HashMap<>();request.setAttribute(attribute,cache,RequestAttributes.SCOPE_REQUEST);}
  if(!cache.containsKey(key))cache.put(key,query.get());
  return (T)cache.get(key);
 }
}
