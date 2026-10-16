const kind = 'lingdong.parent-relationship.invitation';
export function validInvitationId(value: unknown): value is string { return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value) && (value.length < 19 || value <= '9223372036854775807'); }
export function encodeParentInvitation(id: string): string { if (!validInvitationId(id)) throw new Error('邀请编号不合法'); return JSON.stringify({kind,version:1,invitationId:id}); }
export function parseParentInvitation(value: string): string { if(value.length > 512) throw new Error('邀请二维码内容过长'); let data; try {data=JSON.parse(value);} catch {throw new Error('不是陪伴关系邀请二维码');} if(!data || data.kind!==kind || data.version!==1 || !validInvitationId(data.invitationId)) throw new Error('邀请二维码格式不受支持'); return data.invitationId; }
export function invitationRoute(id: string): string { if(!validInvitationId(id)) throw new Error('邀请编号不合法'); return '/pages/parent-relationship-invitation/parent-relationship-invitation?invitationId='+encodeURIComponent(id); }

