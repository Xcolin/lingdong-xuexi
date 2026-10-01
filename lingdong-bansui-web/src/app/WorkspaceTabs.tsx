import { cloneElement, Suspense, useEffect, useLayoutEffect, useRef, useState, type ReactElement } from 'react';
import { Button, ConfigProvider, Dropdown, Modal, Spin } from 'antd';
import { ChevronDown, X } from 'lucide-react';
import { Navigate, useLocation, useNavigate, type RoutesProps } from 'react-router-dom';
export interface WorkspacePage { path: string; label: string }
interface OpenPage { path: string; url: string }
type CloseAction = 'current' | 'others' | 'left' | 'right' | 'all';
const HOME = '/dashboard';

/** Cached pages are scoped to the authenticated App instance, never persisted across users. */
export function WorkspaceTabs({ pages, children }: { pages: WorkspacePage[]; children: ReactElement<RoutesProps> }) {
  const location = useLocation();
  const navigate = useNavigate();
  const active = location.pathname;
  const url = active + location.search + location.hash;
  const allowed = new Set(pages.map(page => page.path));
  const allowedKey = pages.map(page => page.path).join('|');
  const [opened, setOpened] = useState<OpenPage[]>(() => [{ path: HOME, url: HOME }, ...(active !== HOME && allowed.has(active) ? [{ path: active, url }] : [])]);
  const visible = opened.filter(page => allowed.has(page.path));
  const rendered = allowed.has(active) && !visible.some(page => page.path === active) ? [...visible, { path: active, url }] : visible;
  const tabList = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setOpened(previous => {
      const next = previous.filter(page => allowed.has(page.path));
      if (allowed.has(active)) {
        const index = next.findIndex(page => page.path === active);
        if (index < 0) next.push({ path: active, url });
        else if (next[index].url !== url) next[index] = { path: active, url };
      }
      return next.length === previous.length && next.every((page, index) => page === previous[index]) ? previous : next;
    });
  }, [active, url, allowedKey]);

  useLayoutEffect(() => {
    tabList.current?.querySelector('[aria-selected="true"]')?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
  }, [active]);

  // Static confirmation dialogs live outside page portals and must not outlive their route/access.
  useEffect(() => () => Modal.destroyAll(), [active, allowedKey]);

  function targets(action: CloseAction, path: string): OpenPage[] {
    const index = rendered.findIndex(page => page.path === path);
    return rendered.filter((page, i) => page.path !== HOME && (
      action === 'all' || action === 'current' && page.path === path || action === 'others' && page.path !== path
      || action === 'left' && i < index || action === 'right' && i > index));
  }
  function close(action: CloseAction, path: string) {
    const removed = new Set(targets(action, path).map(page => page.path));
    const remaining = rendered.filter(page => !removed.has(page.path));
    setOpened(remaining);
    if (removed.has(active)) {
      const index = rendered.findIndex(page => page.path === active);
      const previous = rendered.slice(0, index).reverse().find(page => !removed.has(page.path));
      navigate(previous?.url ?? remaining[0]?.url ?? HOME, { replace: true });
    }
  }
  function items(path: string) {
    return ([['current', '关闭当前'], ['others', '关闭其他'], ['left', '关闭左侧'], ['right', '关闭右侧'], ['all', '关闭全部']] as const)
      .map(([key, label]) => ({ key, label, disabled: targets(key, path).length === 0, onClick: () => close(key, path) }));
  }

  return <>
    {!allowed.has(active) && <Navigate to={HOME} replace />}
    <div className="workspace-tabs-bar">
      <div className="workspace-tabs-list" ref={tabList} role="tablist" aria-label="已打开页面">
        {rendered.map(page => {
          const label = pages.find(item => item.path === page.path)?.label ?? page.path;
          return <Dropdown key={page.path} menu={{ items: items(page.path) }} trigger={['contextMenu']}>
            <div className={`workspace-tab${active === page.path ? ' is-active' : ''}`}>
              <button type="button" role="tab" id={`workspace-tab-${page.path.slice(1)}`} aria-controls={`workspace-panel-${page.path.slice(1)}`} aria-selected={active === page.path}
                onClick={() => navigate(page.url)} onKeyDown={event => {
                  if (event.key === 'Delete' && page.path !== HOME) close('current', page.path);
                  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'Home' || event.key === 'End') {
                    event.preventDefault();
                    const index = rendered.findIndex(item => item.path === page.path);
                    const next = event.key === 'Home' ? 0 : event.key === 'End' ? rendered.length - 1 : (index + (event.key === 'ArrowRight' ? 1 : -1) + rendered.length) % rendered.length;
                    navigate(rendered[next].url);
                    document.getElementById(`workspace-tab-${rendered[next].path.slice(1)}`)?.focus();
                  }
                }}>{label}</button>
              {page.path !== HOME && <button type="button" className="workspace-tab-close" aria-label={`关闭${label}`} onClick={() => close('current', page.path)}><X size={13} /></button>}
            </div>
          </Dropdown>;
        })}
      </div>
      <Dropdown menu={{ items: items(active) }} trigger={['click']}><Button type="text" aria-label="页签操作" icon={<ChevronDown size={16} />} /></Dropdown>
    </div>
    <div className="workspace-panels">
      {rendered.map(page => <PagePanel key={page.path} path={page.path} active={page.path === active}>
        {cloneElement(children, { location: page.path === active ? url : page.url })}
      </PagePanel>)}
    </div>
  </>;
}

function PagePanel({ path, active, children }: { path: string; active: boolean; children: ReactElement }) {
  const ref = useRef<HTMLDivElement>(null);
  return <section ref={ref} className="workspace-page" hidden={!active} role="tabpanel" id={`workspace-panel-${path.slice(1)}`} aria-labelledby={`workspace-tab-${path.slice(1)}`}>
    <ConfigProvider getPopupContainer={() => ref.current ?? document.body}>
      <Suspense fallback={<div className="route-loading"><Spin /></div>}>{children}</Suspense>
    </ConfigProvider>
  </section>;
}
