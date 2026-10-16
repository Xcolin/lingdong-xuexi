import {describe,it,expect} from 'vitest';
import {formatDateTime} from './datetime';
describe('formatDateTime',()=>{
  it('邀请码有效期去除小数秒并保留原时间',()=>{
    expect(formatDateTime('2026-10-16T10:27:53.754998')).toBe('2026-10-16 10:27:53');
  });
  it('兼容整数秒、缺省秒和空值',()=>{
    expect(formatDateTime('2026-10-16T10:27:53')).toBe('2026-10-16 10:27:53');
    expect(formatDateTime('2026-10-16T10:27')).toBe('2026-10-16 10:27:00');
    expect(formatDateTime(null)).toBe('-');
  });
});
