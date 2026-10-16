import {describe,it,expect} from 'vitest';
import {organizationUserTypeOptions,defaultOrganizationUserType} from './organizationUserTypes';
describe('组织用户类型约束',()=>{
 it.each([
  ['FAMILY',['FAMILY','STUDENT'],'FAMILY'],
  ...['SCHOOL','CAMPUS','GRADE','CLASS'].map(code=>[code,['ORGANIZATION','TEACHER','STUDENT'],'ORGANIZATION']),
  ...['REGION','CITY','COUNTRY'].map(code=>[code,['PLATFORM'],'PLATFORM'])
 ])('%s仅开放适用账号并设置默认值',(code,types,defaultType)=>{
  expect(organizationUserTypeOptions(code as string).map(option=>option.value)).toEqual(types);
  expect(defaultOrganizationUserType(code as string)).toBe(defaultType);
 });
 it.each([undefined,'CUSTOM'])('未指定或自定义类型保留所有账号 %s',code=>{
  expect(organizationUserTypeOptions(code).map(option=>option.value)).toEqual(['PLATFORM','ORGANIZATION','TEACHER','FAMILY','STUDENT']);
 });
});
