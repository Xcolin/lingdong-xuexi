import {describe,it,expect} from 'vitest';
import {registrationDestination} from './registrationDestination';
describe('registrationDestination',()=>{it('keeps empty roles on progress',()=>expect(registrationDestination([],[])).toBe('/registration-progress'));it('prefers an authorized business home',()=>expect(registrationDestination(['/users','/dashboard'],[{canEnterFamily:true,canEnterInstitution:false}])).toBe('/dashboard'));it('does not land in ordinary onboarding menu',()=>expect(registrationDestination(['/my-organizations'],[])).toBe('/registration-progress'));});

it('pending no-org applicant uses progress despite menu',()=>expect(registrationDestination(['/dashboard'],[],true)).toBe('/registration-progress'));
it('platform no membership retains business page',()=>expect(registrationDestination(['/dashboard'],[],false)).toBe('/dashboard'));
