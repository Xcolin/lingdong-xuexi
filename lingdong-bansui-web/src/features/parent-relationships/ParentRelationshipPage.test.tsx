import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { CurrentUser } from '../../api/auth';
import { ParentRelationshipPage } from './ParentRelationshipPage';

const apiMocks = vi.hoisted(() => ({
  listStudents: vi.fn(),
  get: vi.fn(),
  createSecondaryInvitation: vi.fn(),
  createPrimaryTransferInvitation: vi.fn(),
  unbindSecondary: vi.fn(),
  unbindPrimary: vi.fn()
}));

vi.mock('./api', () => ({ parentRelationshipApi: apiMocks }));

const primaryUser: CurrentUser = {
  userId: '8910000000000000901', sessionId: '1', username: 'primary',
  displayName: '主家长', clientType: 'WEB', roleCodes: ['PARENT'], permissionCodes: []
};

const relationship = {
  studentId: '8910000000000000902',
  primaryParentUserId: primaryUser.userId,
  secondaryParentUserId: '8910000000000000903',
  primaryParent: {
    userId: primaryUser.userId, displayName: '主家长', mobileMasked: '138****8000',
    relationshipRole: 'PRIMARY_GUARDIAN' as const
  },
  secondaryParent: {
    userId: '8910000000000000903', displayName: '副家长', mobileMasked: '139****9000',
    relationshipRole: 'SECONDARY_GUARDIAN' as const
  }
};

describe('Web 家长关系管理', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMocks.listStudents.mockResolvedValue([{
      studentId: relationship.studentId, studentName: '小灵', relationshipRole: 'PRIMARY_GUARDIAN'
    }]);
    apiMocks.get.mockResolvedValue(relationship);
    apiMocks.unbindSecondary.mockResolvedValue(undefined);
  });

  it('主家长可发起邀请、转移并经二次确认解除副家长', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><ParentRelationshipPage currentUser={primaryUser} /></MemoryRouter>);

    expect(await screen.findByRole('heading', { name: '家长关系' })).toBeInTheDocument();
    expect(screen.getByText('139****9000')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '邀请副家长' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '转移监护权' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '解除副家长' }));
    await user.click(await screen.findByRole('button', { name: '确认解除' }));
    await waitFor(() => expect(apiMocks.unbindSecondary).toHaveBeenCalledWith(
      relationship.studentId, relationship.secondaryParentUserId
    ));
  });

  it('副家长仅查看关系，不展示任何关系写操作', async () => {
    const secondaryUser = { ...primaryUser, userId: relationship.secondaryParentUserId };
    apiMocks.listStudents.mockResolvedValue([{
      studentId: relationship.studentId, studentName: '小灵', relationshipRole: 'SECONDARY_GUARDIAN'
    }]);
    render(<MemoryRouter><ParentRelationshipPage currentUser={secondaryUser} /></MemoryRouter>);

    expect(await screen.findByText('副家长只读')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '邀请副家长' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '转移监护权' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '解除副家长' })).not.toBeInTheDocument();
  });
});
