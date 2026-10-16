import {organizationOnboardingApi} from '../api/organizationOnboarding';
import {registrationDestination} from '../features/auth/registrationDestination';
import { lazy, useCallback, useEffect, useState } from 'react';
import { Alert, Avatar, Button, ConfigProvider, Dropdown, Layout, Menu, Spin, message } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AppWindow, BookOpenCheck, Building2, Cable, ChevronDown, CircleAlert, ClipboardList, Coins, Database, FileDown, FileSpreadsheet, Gauge, Gift, GraduationCap, KeyRound, LayoutDashboard, LogOut, Paperclip, QrCode, ShieldCheck, UserRoundCog, UsersRound } from 'lucide-react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { organizationContextStore } from '../api/http';
import { authApi, type CurrentUser } from '../api/auth';
import { capabilityApi, type ClientCapabilities } from '../api/capability';
import { RegistrationPage } from '../features/auth/RegistrationPage';
import { Suspense } from 'react';
import { LoginPage } from '../features/auth/LoginPage';
import { ChangePasswordModal } from '../features/auth/ChangePasswordModal';
import { ParentOnboardingPage } from '../features/auth/ParentOnboardingPage';
import { ParentRelationshipInvitationPage } from '../features/parent-relationships/ParentRelationshipInvitationPage';
import { WorkspaceTabs } from './WorkspaceTabs';
import { menuApi, type MenuNode } from '../api/menus';
import { configuredAccess, MenuConfigurationProvider, menuOrderStyles } from './MenuConfiguration';
import { buildMenuNavigation } from './menuNavigation';
import * as NavigationIcons from 'lucide-react';
import { menuPageCatalog } from './menuCatalog';
import { canAccessAttendance } from '../features/attendance/rules';
import { canAccessSystemTasks } from '../features/system-tasks/SystemTaskWorkbench';

const OrganizationOnboardingPage = lazy(async () => ({ default: (await import('../features/organization-onboarding/OrganizationOnboardingPage')).OrganizationOnboardingPage }));
const DashboardPage = lazy(async () => ({ default: (await import('../features/dashboard/DashboardPage')).DashboardPage }));
const MenuManagementPage = lazy(async () => ({ default: (await import('../features/menus/MenuManagementPage')).MenuManagementPage }));
const SystemTaskWorkbench = lazy(async () => ({ default: (await import('../features/system-tasks/SystemTaskWorkbench')).SystemTaskWorkbench }));
const FeatureManagementPage = lazy(async () => ({ default: (await import('../features/feature-management/FeatureManagementPage')).FeatureManagementPage }));
const IamManagementPage = lazy(async () => ({ default: (await import('../features/iam/IamManagementPage')).IamManagementPage }));
const OrganizationManagementPage = lazy(async () => ({ default: (await import('../features/organizations/OrganizationManagementPage')).OrganizationManagementPage }));
const UserManagementPage = lazy(async () => ({ default: (await import('../features/users/UserManagementPage')).UserManagementPage }));
const LearningTaskManagementPage = lazy(async () => ({ default: (await import('../features/learning-tasks/LearningTaskManagementPage')).LearningTaskManagementPage }));
const GrowthPointPage = lazy(async () => ({ default: (await import('../features/growth-points/GrowthPointPage')).GrowthPointPage }));
const RewardManagementPage = lazy(async () => ({ default: (await import('../features/rewards/RewardManagementPage')).RewardManagementPage }));
const GrowthReviewPage = lazy(async () => ({ default: (await import('../features/growth-reviews/GrowthReviewPage')).GrowthReviewPage }));
const AnonymousRankPage = lazy(async () => ({ default: (await import('../features/anonymous-ranks/AnonymousRankPage')).AnonymousRankPage }));
const AnonymousRankWithdrawalPage = lazy(async () => ({ default: (await import('../features/anonymous-ranks/AnonymousRankWithdrawalPage')).AnonymousRankWithdrawalPage }));
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
        <Route path="/register" element={<RegistrationPage />} />
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
  const [configuredMenus, setConfiguredMenus] = useState<MenuNode[]>([]);
  const [menuError, setMenuError] = useState('');
  const [pendingApplication,setPendingApplication]=useState(false);
  const [entryOrganizations,setEntryOrganizations]=useState<Array<{canEnterFamily:boolean;canEnterInstitution:boolean}>>([]);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [passwordChangeRequired, setPasswordChangeRequired] = useState(false);
  const [organizationContextVersion, setOrganizationContextVersion] = useState(0);
  const [organizationContextName, setOrganizationContextName] = useState('');
  const refreshMenus = useCallback(async () => {
    const settings = await authApi.accountSettings();
    setPasswordChangeRequired(settings.passwordChangeRequired);
    if (settings.passwordChangeRequired) return;
    try { const [menus, user, loadedCapabilities] = await Promise.all([menuApi.current(), authApi.currentUser(), capabilityApi.web()]); setConfiguredMenus(menus); if(user.permissionCodes.includes('ORGANIZATION_ONBOARDING_READ')) { setEntryOrganizations(await organizationOnboardingApi.myOrganizations()); setPendingApplication((await organizationOnboardingApi.applications()).some(a=>a.status==='PENDING')); } setCurrentUser(user); setCapabilities(loadedCapabilities); setMenuError(''); }
    catch (error) { setMenuError(error instanceof Error ? error.message : '菜单配置加载失败'); throw error; }
  }, []);
  const updateAttendanceAccess = useCallback((user: CurrentUser, loadedCapabilities: ClientCapabilities) => {
    setCurrentUser(user);
    setCapabilities(loadedCapabilities);
  }, []);

  useEffect(() => {
    if (!authApi.hasLocalSession()) {
      navigate('/login', { replace: true });
      return;
    }
    void Promise.all([authApi.currentUser(), capabilityApi.web(), menuApi.current().catch(error => {
      setMenuError(error instanceof Error ? error.message : '菜单配置加载失败');
      return [] as MenuNode[];
    })])
      .then(async ([user, loadedCapabilities, menus]) => {
        const settings = await authApi.accountSettings();
        setPasswordChangeRequired(settings.passwordChangeRequired);
        if (settings.passwordChangeRequired) return;
        setCurrentUser(user);
        setCapabilities(loadedCapabilities);
        setConfiguredMenus(menus);
        if(user.permissionCodes.includes('ORGANIZATION_ONBOARDING_READ')) {const [orgs,apps]=await Promise.all([organizationOnboardingApi.myOrganizations(),organizationOnboardingApi.applications()]);setEntryOrganizations(orgs);setPendingApplication(apps.some(a=>a.status==='PENDING'));}
      })
      .catch(() => {
        authApi.clearLocalSession();
        navigate('/login', { replace: true });
      })
      .finally(() => setLoading(false));
  }, [navigate]);

  useEffect(() => {
    const reload = () => { if (authApi.hasLocalSession()) void refreshMenus().catch(() => {}); };
    window.addEventListener('focus', reload);
    return () => window.removeEventListener('focus', reload);
  }, [refreshMenus]);

  function endSession(): void {
    authApi.clearLocalSession();
    navigate('/login', { replace: true });
  }

  if (passwordChangeRequired) {
    return <ChangePasswordModal onCancel={endSession} onSuccess={endSession} />;
  }
  if (loading || !currentUser || !capabilities) {
    return <div className="app-loading"><Spin size="large" /></div>;
  }
  if (menuError && configuredMenus.length === 0) {
    return <div className="app-loading"><Alert type="error" message={menuError} action={<Button onClick={() => void refreshMenus().catch(() => {})}>重试</Button>} /></div>;
  }

  const pageAccess = (route: string) => canAccessConfiguredPage(route, menuError ? [] : configuredMenus, currentUser.permissionCodes, capabilities);
  const learningTasksAvailable = pageAccess('/learning-tasks');
  const systemTasksAvailable = pageAccess('/system-tasks');
  const featureManagementAvailable = pageAccess('/feature-management');
  const growthPointsAvailable = pageAccess('/growth-points');
  const rewardsAvailable = pageAccess('/rewards');
  const growthReviewsAvailable = pageAccess('/growth-reviews');
  const anonymousRankAvailable = pageAccess('/anonymous-ranks');
  const rankWithdrawalAvailable = pageAccess('/rank-preferences');
  const studentQrLoginAvailable = pageAccess('/student-login');
  const parentRelationshipsAvailable = pageAccess('/parent-relationships');
  const organizationPageAvailable = pageAccess('/organizations');
  const dictionaryManagementAvailable = pageAccess('/dictionaries');
  const cacheManagementAvailable = pageAccess('/cache-management');
  const interfaceServiceManagementAvailable = pageAccess('/interface-services');
  const attachmentManagementAvailable = pageAccess('/attachment-management');
  const importExportTemplatesAvailable = pageAccess('/import-export-templates');
  const importJobsAvailable = pageAccess('/import-jobs');
  const exportJobsAvailable = pageAccess('/export-jobs');
  const teacherManagementAvailable = pageAccess('/teachers');
  const exceptionReportsAvailable = pageAccess('/exception-reports');
  const attendanceAvailable = pageAccess('/attendance-records');
  const selectedKey = location.pathname.startsWith('/my-organizations') ? 'my-organizations' : location.pathname.startsWith('/menu-management') ? 'menu-management'
    : location.pathname.startsWith('/rank-preferences') ? 'rank-preferences'
    : location.pathname.startsWith('/system-tasks') ? 'system-tasks'
    : location.pathname.startsWith('/feature-management') ? 'feature-management'
    : location.pathname.startsWith('/anonymous-ranks') ? 'anonymous-ranks'
    : location.pathname.startsWith('/attendance-records') ? 'attendance-records'
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

  const registeredMenus = menuPageCatalog.filter(page => pageAccess(page.route)).map(page => ({
    key: page.route, label: page.name,
    icon: (() => { const Icon = NavigationIcons[page.icon as keyof typeof NavigationIcons] as React.ComponentType<{ size: number }>; return Icon ? <Icon size={18} /> : undefined; })()
  }));
  const configuredNavigation = buildMenuNavigation(configuredMenus, registeredMenus.map(item => ({ path: item.key, label: item.label, icon: item.icon })), name => {
    const known = menuPageCatalog.some(page => page.icon === name) || name === 'Folder';
    const Icon = known && name ? NavigationIcons[name as keyof typeof NavigationIcons] as typeof NavigationIcons.Folder : null;
    return Icon ? <Icon size={18} /> : null;
  });

  async function finishAdmission() {
    const user=await authApi.currentUser();
    if(user.parentProfile) { const state=await authApi.parentState(); if(state.onboardingRequired||state.agreementAcceptanceRequired){navigate('/parent-onboarding');return;} }
    await refreshMenus();
    const [menus,caps,orgs,apps]=await Promise.all([menuApi.current(),capabilityApi.web(),organizationOnboardingApi.myOrganizations(),organizationOnboardingApi.applications()]);
    const pages=menuPageCatalog.filter(p=>p.route!=='/my-organizations'&&canAccessConfiguredPage(p.route,menus,user.permissionCodes,caps)).map(p=>p.route);
    navigate(registrationDestination(pages,orgs,apps.some(a=>a.status==='PENDING')||user.userType==='PERSONAL'),{replace:true});
  }
  if (location.pathname === '/registration-progress' || configuredNavigation.pages.length === 0) {
    if(!currentUser.permissionCodes.includes('ORGANIZATION_ONBOARDING_READ'))return <div className="app-loading"><Alert type="info" message="当前账号没有可访问的业务页面" action={<Button onClick={endSession}>退出登录</Button>}/></div>;
    return <Suspense fallback={<Spin />}><OrganizationOnboardingPage onLogout={endSession} currentUser={currentUser} onOrganizationsChanged={finishAdmission} onEnterOrganization={organization=>{organizationContextStore.select(organization.id);setOrganizationContextName(organization.name);setOrganizationContextVersion(v=>v+1);void finishAdmission().catch(e=>message.error(e.message));}} /></Suspense>;
  }
  return (
    <MenuConfigurationProvider nodes={menuError ? [] : configuredMenus} permissionCodes={currentUser.permissionCodes} refresh={refreshMenus}>
    <style>{menuOrderStyles(configuredMenus)}</style>
    <Layout className="app-shell">
      <Sider width={232} breakpoint="lg" collapsedWidth={64} className="app-sider">
        <div className="brand-lockup"><AppWindow size={22} aria-hidden="true" /><span>灵动伴随</span></div>
        <Menu
          mode="inline"
          theme="dark"
          selectedKeys={[`/${selectedKey}`]}
          onClick={({ key }) => navigate(key)}
          items={configuredNavigation.items}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <span className="header-context">{organizationContextName || '管理端'}</span>
          <Dropdown menu={{ items: [
            { key: 'password', icon: <KeyRound size={16} />, label: '修改密码', onClick: () => setPasswordOpen(true) },
            { key: 'logout', icon: <LogOut size={16} />, label: '退出登录', onClick: endSession }
          ] }} trigger={['click']}>
            <Button type="text" className="user-menu"><Avatar size="small" icon={<KeyRound size={14} />} />{currentUser.displayName}<ChevronDown size={16} /></Button>
          </Dropdown>
        </Header>
        <Content className="app-content">
          {menuError && <Alert type="error" message={menuError} action={<Button onClick={() => void refreshMenus().catch(() => {})}>重试</Button>} />}
          <WorkspaceTabs key={`${currentUser.userId}:${organizationContextVersion}`} pages={configuredNavigation.pages}>
            <Routes>
              <Route path="/" element={<Navigate to={registrationDestination(registeredMenus.map(p=>p.key),entryOrganizations,pendingApplication || currentUser.userType === 'PERSONAL')} replace />} />
              <Route path="/my-organizations" element={pageAccess('/my-organizations') ? <OrganizationOnboardingPage currentUser={currentUser} onOrganizationsChanged={async () => { const user = await authApi.currentUser(); if(user.parentProfile) { const state = await authApi.parentState(); if(state.onboardingRequired || state.agreementAcceptanceRequired) { navigate('/parent-onboarding'); return; } } await refreshMenus(); }} onEnterOrganization={organization => { organizationContextStore.select(organization.id); setOrganizationContextName(organization.name); setOrganizationContextVersion(previous => previous + 1); void refreshMenus().then(() => navigate(`/organizations?organizationId=${encodeURIComponent(organization.id)}`)).catch(() => {}); }} /> : <Navigate to="/login" replace />} />
              <Route path="/registration-progress" element={currentUser.permissionCodes.includes('ORGANIZATION_ONBOARDING_READ') ? <OrganizationOnboardingPage currentUser={currentUser} onOrganizationsChanged={async () => { const user = await authApi.currentUser(); if(user.parentProfile) { const state = await authApi.parentState(); if(state.onboardingRequired || state.agreementAcceptanceRequired) { navigate('/parent-onboarding'); return; } } await refreshMenus(); }} onEnterOrganization={organization => { organizationContextStore.select(organization.id); setOrganizationContextName(organization.name); setOrganizationContextVersion(previous => previous + 1); void refreshMenus().then(() => navigate(`/organizations?organizationId=${encodeURIComponent(organization.id)}`)).catch(() => {}); }} /> : <Navigate to="/login" replace />} />
              <Route path="/system-tasks" element={systemTasksAvailable
                ? <SystemTaskWorkbench key={currentUser.userId} currentUser={currentUser} onNavigate={navigate} onAccessChange={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/feature-management" element={featureManagementAvailable
                ? <FeatureManagementPage key={currentUser.userId} currentUser={currentUser} onAccessChange={updateAttendanceAccess} />
                : <Navigate to="/dashboard" replace />} />
              <Route path="/dashboard" element={(
                pageAccess('/dashboard') ? <DashboardPage
                  currentUser={currentUser}
                  accountSecurityManagementEnabled={capabilities.accountSecurityManagementEnabled}
                  learningTaskManagementEnabled={capabilities.learningTaskManagementEnabled}
                  parentAccountLifecycleEnabled={capabilities.parentAccountLifecycleEnabled}
                  attendanceAvailable={attendanceAvailable}
                  onOpenAttendance={() => navigate('/attendance-records')}
                  onSessionEnded={endSession}
                /> : <Navigate to="/login" replace />
              )} />
              <Route path="/users" element={pageAccess('/users') ? <UserManagementPage currentUser={currentUser} /> : <Navigate to="/dashboard" replace />} />
              <Route path="/iam" element={pageAccess('/iam') ? <IamManagementPage permissionCodes={currentUser.permissionCodes} roleCodes={currentUser.roleCodes} /> : <Navigate to="/dashboard" replace />} />
              <Route path="/menu-management" element={pageAccess('/menu-management')
                ? <MenuManagementPage currentUser={currentUser} /> : <Navigate to="/dashboard" replace />} />
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
                      accessRevision={JSON.stringify([currentUser.userId, currentUser.roleCodes, currentUser.permissionCodes, capabilities])}
                      canRead={currentUser.permissionCodes.includes('EXPORT_JOB_READ')}
                      canCreateOrdinary={currentUser.parentProfile === true && currentUser.permissionCodes.includes('GROWTH_POINT_READ_CHILD') && currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')}
                      canExportDictionary={capabilities.dictionaryManagementEnabled === true
                        && currentUser.permissionCodes.includes('DICTIONARY_READ')
                        && currentUser.permissionCodes.includes('DICTIONARY_EXPORT')}
                      canExportTemplate={capabilities.importExportTemplateManagementEnabled === true
                        && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_READ')
                        && currentUser.permissionCodes.includes('IMPORT_EXPORT_TEMPLATE_EXPORT')}
                      canExportInterface={canExportInterfaceLedger(currentUser, capabilities)}
                      canExportCache={canExportCache(currentUser, capabilities)}
                      canExportSystemTasks={canExportSystemTasks(currentUser, capabilities)}
                      canExportRewards={canExportRewards(currentUser, capabilities)}
                      canExportStudentTasks={canExportStudentTasks(currentUser, capabilities)}
                      canExportOrgTaskStatistics={canExportOrgTaskStatistics(currentUser, capabilities)}
                      canExportAttendanceLedger={canExportAttendanceLedger(currentUser, capabilities)}
                      canExportAttachments={canExportAttachments(currentUser, capabilities)}
                      canExportExceptions={canExportExceptions(currentUser, capabilities)}
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
                  ? <GrowthReviewPage subscriptionEnabled={capabilities.growthReviewSubscriptionEnabled === true
                      }
                    canEnableSubscription={currentUser.permissionCodes.includes('GROWTH_REVIEW_SUBSCRIBE_CHILD')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')}
                    canCreateExport={capabilities.growthReviewPdfExportEnabled === true
                      && currentUser.permissionCodes.includes('EXPORT_JOB_CREATE')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')}
                    canReadExportHistory={capabilities.attachmentServiceEnabled === true
                      && currentUser.permissionCodes.includes('EXPORT_JOB_READ')
                      && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD')} />
                  : <Navigate to="/dashboard" replace />}
              />
              <Route path="/anonymous-ranks" element={anonymousRankAvailable
                ? <AnonymousRankPage /> : <Navigate to="/dashboard" replace />} />
              <Route path="/rank-preferences" element={rankWithdrawalAvailable
                ? <AnonymousRankWithdrawalPage /> : <Navigate to="/dashboard" replace />} />
              <Route
                path="/student-login"
                element={studentQrLoginAvailable
                  ? <StudentLoginManagementPage
                      studentQrLoginEnabled={capabilities.studentQrLoginEnabled === true}
                      studentWechatAuthEnabled={capabilities.studentWechatAuthEnabled === true}
                      canManageStudentWechat={currentUser.permissionCodes.includes('STUDENT_WECHAT_UNBIND')}
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
          </WorkspaceTabs>
        </Content>
      </Layout>
    </Layout>
      {passwordOpen && <ChangePasswordModal onCancel={() => setPasswordOpen(false)} onSuccess={() => {
        setPasswordOpen(false);
        message.success('密码修改成功，请使用新密码重新登录');
        endSession();
      }} />}
    </MenuConfigurationProvider>
  );
}

export function canAccessLearningTasks(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.learningTaskManagementEnabled
    && currentUser.permissionCodes.includes('LEARNING_TASK_READ_MANAGED');
}

export function canAccessGrowthPoints(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.growthPointQueryEnabled && currentUser.permissionCodes.includes('GROWTH_POINT_READ_CHILD');
}

export function canAccessRewards(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.rewardExchangeEnabled && currentUser.permissionCodes.some(code => ['REWARD_MANAGE_CHILD', 'REWARD_EXCHANGE_REVIEW_CHILD'].includes(code));
}

export function canAccessGrowthReviews(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return (capabilities.dailyGrowthReviewEnabled === true || capabilities.periodicGrowthReportEnabled === true)
    && currentUser.permissionCodes.includes('GROWTH_REVIEW_READ_CHILD');
}

export function canAccessStudentQrLogin(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return currentUser.permissionCodes.some(code=>['STUDENT_LOGIN_READ','STUDENT_READ'].includes(code))
    && (capabilities.studentQrLoginEnabled === true
      || (capabilities.studentWechatAuthEnabled === true));
}

export function canAccessParentRelationships(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.parentRelationshipManagementEnabled
    && currentUser.permissionCodes.includes('PARENT_RELATIONSHIP_READ');
}

export function canAccessOrganizationPage(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return (capabilities.organizationManagementEnabled === true && currentUser.permissionCodes.some(code => ['ORG_NODE_READ', 'ORG_NODE_CHANGE_REVIEW'].includes(code))) || currentUser.permissionCodes.includes('CLASS_READ');
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

export function canExportInterfaceLedger(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true
    && capabilities.attachmentServiceEnabled === true
    && capabilities.interfaceServiceManagementEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_READ')
    && currentUser.permissionCodes.includes('INTERFACE_SERVICE_EXPORT');
}

export function canAccessTeacherManagement(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.teacherManagementEnabled === true

    && currentUser.permissionCodes.includes('TEACHER_READ');
}

export function canAccessExceptionReports(
  currentUser: CurrentUser,
  capabilities: ClientCapabilities
): boolean {
  return capabilities.studentExceptionReportEnabled === true
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_READ')
;
}

export function canExportCache(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.cacheManagementEnabled === true
    && currentUser.permissionCodes.includes('CACHE_READ') && currentUser.permissionCodes.includes('CACHE_EXPORT');
}

export function canExportSystemTasks(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true

    && currentUser.permissionCodes.includes('SYSTEM_TASK_READ') && currentUser.permissionCodes.includes('SYSTEM_TASK_EXPORT');
}

export function canExportRewards(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.rewardExchangeEnabled === true
    && currentUser.permissionCodes.includes('REWARD_EXCHANGE_READ')
    && currentUser.permissionCodes.includes('REWARD_EXCHANGE_EXPORT');
}

export function canExportExceptions(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.studentExceptionReportEnabled === true

    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_READ')
    && currentUser.permissionCodes.includes('EXCEPTION_REPORT_EXPORT');
}

export function canExportAttachments(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true
    && currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_READ')
    && currentUser.permissionCodes.includes('ATTACHMENT_FILE_LEDGER_EXPORT');
}

/** 任务读取范围由服务端授权数据范围决定。 */
export function canExportStudentTasks(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.learningTaskManagementEnabled === true
    && currentUser.permissionCodes.includes(currentUser.parentProfile === true ? 'LEARNING_TASK_READ_MANAGED' : 'LEARNING_TASK_PROGRESS_READ')
    && currentUser.permissionCodes.includes('STUDENT_TASK_REPORT_EXPORT');
}

/** 机构任务统计要求读取及导出权限。 */
export function canExportOrgTaskStatistics(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.learningTaskManagementEnabled === true

    && currentUser.permissionCodes.includes('ORGANIZATION_TASK_STATISTICS_EXPORT')
    && currentUser.permissionCodes.includes('LEARNING_TASK_PROGRESS_READ');
}

/** 考勤台账要求读取、导出权限及考勤开关。 */
export function canExportAttendanceLedger(currentUser: CurrentUser, capabilities: ClientCapabilities): boolean {
  return capabilities.dataExportEnabled === true && capabilities.attachmentServiceEnabled === true
    && capabilities.importExportTemplateManagementEnabled === true && capabilities.attendanceManagementEnabled === true

    && currentUser.permissionCodes.includes('ATTENDANCE_LEDGER_EXPORT')
    && currentUser.permissionCodes.includes('ATTENDANCE_READ');
}

/** One admission rule for navigation and direct routes. Server codes already include feature/scope eligibility. */
export function canAccessConfiguredPage(route: string, nodes: MenuNode[], permissionCodes: string[], capabilities: ClientCapabilities): boolean {
  const page = nodes.find(node => node.type === 'PAGE' && node.route === route);
  if (!page || !configuredAccess({ nodes, permissionCodes, refresh: async () => {} }, page.code)) return false;
  const flags: Record<string, boolean> = {
    '/attendance-records': capabilities.attendanceManagementEnabled === true,
    '/learning-tasks': capabilities.learningTaskManagementEnabled === true,
    '/exception-reports': capabilities.studentExceptionReportEnabled === true,
    '/growth-points': capabilities.growthPointQueryEnabled === true,
    '/rewards': capabilities.rewardExchangeEnabled === true,
    '/growth-reviews': capabilities.dailyGrowthReviewEnabled === true || capabilities.periodicGrowthReportEnabled === true,
    '/anonymous-ranks': capabilities.anonymousClassRankEnabled === true,
    '/parent-relationships': capabilities.parentRelationshipManagementEnabled === true,
    '/teachers': capabilities.teacherManagementEnabled === true,
    '/dictionaries': capabilities.dictionaryManagementEnabled === true,
    '/cache-management': capabilities.cacheManagementEnabled === true,
    '/interface-services': capabilities.interfaceServiceManagementEnabled === true,
    '/attachment-management': capabilities.attachmentServiceEnabled === true,
    '/import-export-templates': capabilities.importExportTemplateManagementEnabled === true,
    '/import-jobs': capabilities.dataImportValidationEnabled === true,
    '/export-jobs': capabilities.dataExportEnabled === true,
    '/organizations': capabilities.organizationManagementEnabled === true || capabilities.classManagementEnabled === true
  };
  return capabilities.client === 'WEB' && (flags[route] ?? true);
}

