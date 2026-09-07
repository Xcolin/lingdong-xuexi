import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Avatar, Button, ConfigProvider, Dropdown, Layout, Menu, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AppWindow, BookOpenCheck, Building2, Cable, ChevronDown, CircleAlert, ClipboardList, Coins, Database, FileDown, FileSpreadsheet, Gauge, Gift, GraduationCap, KeyRound, LayoutDashboard, LogOut, Paperclip, QrCode, ShieldCheck, UserRoundCog, UsersRound } from 'lucide-react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { authApi, type CurrentUser } from '../api/auth';
import { capabilityApi, type ClientCapabilities } from '../api/capability';
import { LoginPage } from '../features/auth/LoginPage';
import { ParentOnboardingPage } from '../features/auth/ParentOnboardingPage';
import { ParentRelationshipInvitationPage } from '../features/parent-relationships/ParentRelationshipInvitationPage';
import { canAccessAttendance } from '../features/attendance/rules';

const DashboardPage = lazy(async () => ({ default: (await import('../features/dashboard/DashboardPage')).DashboardPage }));
const IamManagementPage = lazy(async () => ({ default: (await import('../features/iam/IamManagementPage')).IamManagementPage }));
const OrganizationManagementPage = lazy(async () => ({ default: (await import('../features/organizations/OrganizationManagementPage')).OrganizationManagementPage }));
const UserManagementPage = lazy(async () => ({ default: (await import('../features/users/UserManagementPage')).UserManagementPage }));
const LearningTaskManagementPage = lazy(async () => ({ default: (await import('../features/learning-tasks/LearningTaskManagementPage')).LearningTaskManagementPage }));
const GrowthPointPage = lazy(async () => ({ default: (await import('../features/growth-points/GrowthPointPage')).GrowthPointPage }));
const RewardManagementPage = lazy(async () => ({ default: (await import('../features/rewards/RewardManagementPage')).RewardManagementPage }));
const GrowthReviewPage = lazy(async () => ({ default: (await import('../features/growth-reviews/GrowthReviewPage')).GrowthReviewPage }));
const StudentLoginManagementPage = lazy(async () => ({ default: (await import('../features/student-login/StudentLoginManagementPage')).StudentLoginManagementPage }));
const ParentRelationshipPage = lazy(async () => ({ default: (await import('../features/parent-relationships/ParentRelationshipPage')).ParentRelationshipPage }));
const DictionaryManagementPage = lazy(async () => ({ default: (await import('../features/dictionaries/DictionaryManagementPage')).DictionaryManagementPage }));
const CacheManagementPage = lazy(async () => ({ default: (await import('../features/cache/CacheManagementPage')).CacheManagementPage }));
const InterfaceServiceManagementPage = lazy(async () => ({ default: (await import('../features/interface-services/InterfaceServiceManagementPage')).InterfaceServiceManagementPage }));
const AttachmentManagementPage = lazy(async () => ({ default: (await import('../features/attachments/AttachmentManagementPage')).AttachmentManagementPage }));
const ImportExportTemplateManagementPage = lazy(async () => ({ default: (await import('../features/import-export-templates/ImportExportTemplateManagementPage')).ImportExportTemplateManagementPage }));
const ImportJobManagementPage = lazy(async () => ({ default: (await import('../features/import-jobs/ImportJobManagementPage')).ImportJobManagementPage }));
const ExportJobManagementPage = lazy(async () => ({ default: (await import('../features/export-jobs/ExportJobManagementPage')).ExportJobManagementPage }));
const TeacherManagementPage = lazy(async () => ({ default: (await import('../features/teachers/TeacherManagementPage')).TeacherManagementPage }));
const ExceptionReportPage = lazy(async () => ({ default: (await import('../features/exception-reports/ExceptionReportPage')).ExceptionReportPage }));
const AttendancePage = lazy(async () => ({ default: (await import('../features/attendance/AttendancePage')).AttendancePage }));

const { Header, Sider, Content } = Layout;

export function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/parent-onboarding" element={<ParentOnboardingPage />} />
        <Route path="/parent-relationship-invitations/:invitationId" element={<ParentRelationshipInvitationPage />} />
        <Route path="/*" element={<ProtectedManagementApp />} />
      </Routes>
    </ConfigProvider>
  );
}

function ProtectedManagementApp() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [capabilities, setCapabilities] = useState<ClientCapabilities | null>(null);
  const [loading, setLoading] = useState(true);
  const updateAttendanceAccess = useCallback((user: CurrentUser, loadedCapabilities: ClientCapabilities) => {
    setCurrentUser(user);
    setCapabilities(loadedCapabilities);
  }, []);

  useEffect(() => {
    if (!authApi.hasLocalSession()) {
      navigate('/login', { replace: true });
      return;
    }
    void Promise.all([authApi.currentUser(), capabilityApi.web()])
      .then(([user, loadedCapabilities]) => {
        setCurrentUser(user);
        setCapabilities(loadedCapabilities);
      })
      .catch(() => {
        authApi.clearLocalSession();
        navigate('/login', { replace: true });
      })
      .finally(() => setLoading(false));
  }, [navigate]);

  function endSession(): void {
    authApi.clearLocalSession();
    navigate('/login', { replace: true });
  }

  if (loading || !currentUser || !capabilities) {
    return <div className="app-loading"><Spin size="large" /></div>;
  }

  const learningTasksAvailable = canAccessLearningTasks(currentUser, capabilities);
  const growthPointsAvailable = canAccessGrowthPoints(currentUser, capabilities);
  const rewardsAvailable = canAccessRewards(currentUser, capabilities);
  const growthReviewsAvailable = canAccessGrowthReviews(currentUser, capabilities);
  const studentQrLoginAvailable = canAccessStudentQrLogin(currentUser, capabilities);
  const parentRelationshipsAvailable = canAccessParentRelationships(currentUser, capabilities);
  const organizationPageAvailable = canAccessOrganizationPage(currentUser, capabilities);
  const dictionaryManagementAvailable = canAccessDictionaryManagement(currentUser, capabilities);
  const cacheManagementAvailable = canAccessCacheManagement(currentUser, capabilities);
  const interfaceServiceManagementAvailable = canAccessInterfaceServiceManagement(currentUser, capabilities);
  const attachmentManagementAvailable = canAccessAttachmentManagement(currentUser, capabilities);
  const importExportTemplatesAvailable = canAccessImportExportTemplates(currentUser, capabilities);
  const importJobsAvailable = canAccessImportJobs(currentUser, capabilities);
  const exportJobsAvailable = canAccessExportJobs(currentUser, capabilities);
  const teacherManagementAvailable = canAccessTeacherManagement(currentUser, capabilities);
  const exceptionReportsAvailable = canAccessExceptionReports(currentUser, capabilities);
  const attendanceAvailable = canAccessAttendance(currentUser, capabilities.attendanceManagementEnabled);
  const selectedKey = location.pathname.startsWith('/attendance-records') ? 'attendance-records'
    : location.pathname.startsWith('/exception-reports') ? 'exception-reports'
    : location.pathname.startsWith('/export-jobs') ? 'export-jobs'
    : location.pathname.startsWith('/import-jobs') ? 'import-jobs'
    : location.pathname.startsWith('/parent-relationships') ? 'parent-relationships'
    : location.pathname.startsWith('/import-export-templates') ? 'import-export-templates'
    : location.pathname.startsWith('/attachment-management') ? 'attachment-management'
    : location.pathname.startsWith('/interface-services') ? 'interface-services'
    : location.pathname.startsWith('/cache-management') ? 'cache-management'
    : location.pathname.startsWith('/dictionaries') ? 'dictionaries'
    : location.pathname.startsWith('/student-login') ? 'student-login'
    : location.pathname.startsWith('/growth-reviews') ? 'growth-reviews'
    : location.pathname.startsWith('/rewards') ? 'rewards'
    : location.pathname.startsWith('/growth-points') ? 'growth-points'
    : location.pathname.startsWith('/teachers') ? 'teachers'
    : location.pathname.startsWith('/learning-tasks') ? 'learning-tasks'
    : location.pathname.startsWith('/users') ? 'users'
    : location.pathname.startsWith('/organizations') ? 'organizations'
    : location.pathname.startsWith('/iam') ? 'iam' : 'dashboard';

  return (
    <Layout className="app-shell">
      <Sider width={232} breakpoint="lg" collapsedWidth={64} className="app-sider">
        <div className="brand-lockup"><AppWindow size={22} aria-hidden="true" /><span>灵动学习</span></div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[selectedKey]}
          onClick={({ key }) => navigate(key === 'dashboard' ? '/dashboard' : `/${key}`)}
          items={[
            { key: 'dashboard', icon: <LayoutDashboard size={18} />, label: '工作台' },
            attendanceAvailable
              ? { key: 'attendance-records', icon: <BookOpenCheck size={18} />, label: '考勤台账' }
              : null,
            learningTasksAvailable
              ? { key: 'learning-tasks', icon: <ClipboardList size={18} />, label: '学习任务' }
              : null,
            exceptionReportsAvailable
              ? { key: 'exception-reports', icon: <CircleAlert size={18} />, label: '异常报备' }
              : null,
            growthPointsAvailable
              ? { key: 'growth-points', icon: <Coins size={18} />, label: '积分台账' }
              : null,
            rewardsAvailable
              ? { key: 'rewards', icon: <Gift size={18} />, label: '奖励管理' }
              : null,
            growthReviewsAvailable
              ? { key: 'growth-reviews', icon: <BookOpenCheck size={18} />, label: '成长复盘' }
              : null,
            studentQrLoginAvailable
              ? { key: 'student-login', icon: <QrCode size={18} />, label: '学生登录' }
              : null,
            parentRelationshipsAvailable
              ? { key: 'parent-relationships', icon: <UserRoundCog size={18} />, label: '家长关系' }
              : null,
            teacherManagementAvailable
              ? { key: 'teachers', icon: <GraduationCap size={18} />, label: '教师管理' }
              : null,
            { key: 'users', icon: <UsersRound size={18} />, label: '用户管理' },
            { key: 'iam', icon: <ShieldCheck size={18} />, label: '角色与权限' },
            dictionaryManagementAvailable
              ? { key: 'dictionaries', icon: <Database size={18} />, label: '数据字典' }
              : null,
            cacheManagementAvailable
              ? { key: 'cache-management', icon: <Gauge size={18} />, label: '缓存管理' }
              : null,
            interfaceServiceManagementAvailable
              ? { key: 'interface-services', icon: <Cable size={18} />, label: '接口服务' }
              : null,
            attachmentManagementAvailable
              ? { key: 'attachment-management', icon: <Paperclip size={18} />, label: '附件管理' }
              : null,
            importExportTemplatesAvailable
              ? { key: 'import-export-templates', icon: <FileSpreadsheet size={18} />, label: '导入导出模板' }
              : null,
            importJobsAvailable
              ? { key: 'import-jobs', icon: <FileSpreadsheet size={18} />, label: '导入校验作业' }
              : null,
            exportJobsAvailable
              ? { key: 'export-jobs', icon: <FileDown size={18} />, label: '数据导出中心' }
              : null,
            organizationPageAvailable
              ? { key: 'organizations', icon: <Building2 size={18} />, label: currentUser.roleCodes.includes('ORG_ADMIN') ? '机构业务' : '组织管理' }
              : null
          ]}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <span className="header-context">管理端</span>
          <Dropdown menu={{ items: [{ key: 'logout', icon: <LogOut size={16} />, label: '退出登录', onClick: endSession }] }} trigger={['click']}>
            <Button type="text" className="user-menu"><Avatar size="small" icon={<KeyRound size={14} />} />{currentUser.displayName}<ChevronDown size={16} /></Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          <Suspense fallback={<div className="route-loading"><Spin /></div>}>
            <Routes>
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={(
                <DashboardPage
                  currentUser={currentUser}
                  accountSecurityManagementEnabled={capabilities.accountSecurityManagementEnabled}
                  parentAccountLifecycleEnabled={capabilities.parentAccountLifecycleEnabled}
                  attendanceAvailable={attendanceAvailable}
                  onOpenAttendance={() => navigate('/attendance-records')}
                  onSessionEnded={endSession}
                />
              )} />
              <Route path="/users" element={<UserManagementPage />} />
              <Route path="/iam" element={<IamManagementPage />} />
              <Route path="/attendance-records" element={attendanceAvailable
                ? <AttendancePage currentUser={currentUser} onAccessUpdated={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/exception-reports" element={exceptionReportsAvailable
                ? <ExceptionReportPage currentUser={currentUser} />
                : <Navigate to="/dashboard" replace />} />
              <Route
                path="/dictionaries"
                element={dictionaryManagementAvailable
                  ? <DictionaryManagementPage
                      canManage={currentUser.permissionCodes.includes('DICTIONARY_MANAGE')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/cache-management"
                element={cacheManagementAvailable
                  ? <CacheManagementPage
                      canManage={currentUser.permissionCodes.includes('CACHE_MANAGE')}
                      canReview={currentUser.permissionCodes.includes('CACHE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/interface-services"
                element={interfaceServiceManagementAvailable
                  ? <InterfaceServiceManagementPage
                      canManage={currentUser.permissionCodes.includes('INTERFACE_SERVICE_MANAGE')}
                      canReview={currentUser.permissionCodes.includes('INTERFACE_SERVICE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/attachment-management"
                element={attachmentManagementAvailable
                  ? <AttachmentManagementPage
                      canReadRules={currentUser.permissionCodes.includes('ATTACHMENT_RULE_READ')}
                      canManage={currentUser.permissionCodes.includes('ATTACHMENT_RULE_MANAGE')}
                      canReadFiles={currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_READ')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/import-export-templates"
                element={importExportTemplatesAvailable
                  ? <ImportExportTemplateManagementPage
                      canManage={currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_MANAGE')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/import-jobs"
                element={importJobsAvailable
                  ? <ImportJobManagementPage
                      canCreate={currentUser.permissionCodes.includes('IMPORT_JOB_CREATE')}
                      canExecuteStudentImport={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_EXECUTE')}
                      canReadStudentImport={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_RESULT_READ')}
                      canDownloadStudentCredentials={capabilities.studentBatchImportEnabled === true
                        && currentUser.permissionCodes.includes('STUDENT_IMPORT_CREDENTIAL_DOWNLOAD')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/export-jobs"
                element={exportJobsAvailable
                  ? <ExportJobManagementPage
                      canRead={currentUser.permissionCodes.includes('EXPORT_JOB_READ')}
                      canCreateOrdinary={currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')}
                      canSubmitSensitive={currentUser.permissionCodes.includes('EXPORT_SENSITIVE_SUBMIT')}
                      canReview={currentUser.permissionCodes.includes('EXPORT_SENSITIVE_REVIEW')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="/organizations" element={(
                organizationPageAvailable ? <OrganizationManagementPage
                  currentUser={currentUser}
                  organizationManagementEnabled={capabilities.organizationManagementEnabled === true}
                  studentOrganizationRelationshipEnabled={capabilities.studentOrganizationRelationshipEnabled}
                  parentMobileManualRecoveryEnabled={capabilities.parentMobileManualRecoveryEnabled === true}
                  studentAccountCancellationEnabled={capabilities.studentAccountCancellationEnabled === true}
                  classManagementEnabled={capabilities.classManagementEnabled === true}
                /> : <Navigate to="/dashboard" replace />
              )} />
              <Route
                path="/teachers"
                element={teacherManagementAvailable
                  ? <TeacherManagementPage permissionCodes={currentUser.permissionCodes} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/learning-tasks"
                element={learningTasksAvailable
                  ? <LearningTaskManagementPage
                      currentUser={currentUser}
                      previousDayTaskCopyEnabled={capabilities.previousDayTaskCopyEnabled}
                      learningTaskTemplateEnabled={capabilities.learningTaskTemplateEnabled}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/growth-points"
                element={growthPointsAvailable
                  ? <GrowthPointPage
                      correctionEnabled={capabilities.growthPointCorrectionEnabled}
                      dataExportEnabled={capabilities.dataExportEnabled === true}
                      canCreateExport={currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/rewards"
                element={rewardsAvailable
                  ? <RewardManagementPage />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/growth-reviews"
                element={growthReviewsAvailable
                  ? <GrowthReviewPage />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/student-login"
                element={studentQrLoginAvailable
                  ? <StudentLoginManagementPage
                      studentWechatAuthEnabled={capabilities.studentWechatAuthEnabled === true}
                      canManageStudentWechat={currentUser.roleCodes.includes('PARENT')}
                    />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route
                path="/parent-relationships"
                element={parentRelationshipsAvailable
                  ? <ParentRelationshipPage currentUser={currentUser} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  );
}

export function canAccessLearningTasks(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.learningTaskManagementEnabled
    && currentUser.roleCodes.some((role) => ['PARENT', 'ORG_ADMIN', 'TEACHER'].includes(role));
}

export function canAccessGrowthPoints(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.growthPointQueryEnabled && currentUser.roleCodes.includes('PARENT');
}

export function canAccessRewards(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.rewardExchangeEnabled && currentUser.roleCodes.includes('PARENT');
}

export function canAccessGrowthReviews(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return (capabilities.dailyGrowthReviewEnabled || capabilities.periodicGrowthReportEnabled)
    && currentUser.roleCodes.includes('PARENT');
}

export function canAccessStudentQrLogin(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return (capabilities.studentQrLoginEnabled
      && currentUser.roleCodes.some((role) => ['PARENT', 'ORG_ADMIN'].includes(role)))
    || (capabilities.studentWechatAuthEnabled === true && currentUser.roleCodes.includes('PARENT'));
}

export function canAccessParentRelationships(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.parentRelationshipManagementEnabled
    && currentUser.roleCodes.includes('PARENT');
}

export function canAccessOrganizationPage(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  if (currentUser.roleCodes.includes('ORG_ADMIN')) {
    return true;
  }
  return capabilities.organizationManagementEnabled === true
    && currentUser.roleCodes.some((role) => ['SYS_ADMIN', 'SYS_AUDITOR'].includes(role));
}

export function canAccessDictionaryManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dictionaryManagementEnabled === true
    && currentUser.permissionCodes.includes('DICTIONARY_READ');
}

export function canAccessCacheManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.cacheManagementEnabled === true
    && currentUser.permissionCodes.includes('CACHE_READ');
}

export function canAccessInterfaceServiceManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.interfaceServiceManagementEnabled === true
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_READ');
}

export function canAccessAttachmentManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.attachmentServiceEnabled === true
    && currentUser.permissionCodes.some((permission) =>
      ['ATTACHMENT_RULE_READ', 'ATTACHMENT_FILE_LEDGER_READ'].includes(permission));
}

export function canAccessImportExportTemplates(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.importExportTemplateManagementEnabled === true
    && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_READ');
}

export function canAccessImportJobs(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dataImportValidationEnabled === true
    && currentUser.permissionCodes.includes('IMPORT_JOB_READ');
}

export function canAccessExportJobs(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.dataExportEnabled === true
    && currentUser.permissionCodes.some((permission) =>
      ['EXPORT_JOB_READ', 'EXPORT_SENSITIVE_REVIEW'].includes(permission));
}

export function canAccessTeacherManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.teacherManagementEnabled === true
    && currentUser.roleCodes.includes('ORG_ADMIN')
    && currentUser.permissionCodes.includes('TEACHER_READ');
}

export function canAccessExceptionReports(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.studentExceptionReportEnabled === true
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_READ')
    && currentUser.roleCodes.some((role) => ['TEACHER', 'ORG_ADMIN'].includes(role));
}
