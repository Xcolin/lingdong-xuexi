<script lang="ts">
export default {
  // 应用级生命周期入口，后续仅在需要时增加全局初始化逻辑。
};
</script>

<style lang="scss">
page {
  min-height: 100%;
  background: $ld-bg;
  color: $ld-text;
  font-size: $ld-font-body;
  line-height: 1.5;
}

/* ===== 灵动伴随全局公共样式(页面按需组合使用) ===== */

/* 页面壳 */
.ld-page {
  min-height: 100vh;
  padding: 32rpx;
  box-sizing: border-box;
  background: $ld-bg;
}

/* 顶栏:品牌 + 账号 + 退出 */
.ld-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 24rpx 28rpx;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
}
.ld-topbar-brand {
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-width: 0;
}
.ld-topbar-logo {
  width: 76rpx;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  background: $ld-gradient-brand;
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.ld-topbar-name {
  display: block;
  color: $ld-text;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.2;
}
.ld-topbar-meta {
  display: block;
  margin-top: 6rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
  line-height: 1.2;
}
.ld-logout {
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0 26rpx;
  border-radius: $ld-radius-pill;
  background: $ld-danger-soft;
  color: $ld-danger;
  font-size: $ld-font-caption;
  font-weight: 600;
  flex-shrink: 0;
}
.ld-logout::after { border: 0; }

/* 页面标题区 */
.ld-heading { padding: 40rpx 8rpx 8rpx; }
.ld-heading-title {
  display: block;
  color: $ld-text;
  font-size: $ld-font-title;
  font-weight: 700;
  line-height: 1.3;
}
.ld-heading-rule {
  width: 72rpx;
  height: 8rpx;
  margin-top: 18rpx;
  border-radius: 4rpx;
  background: $ld-accent;
}
.ld-heading-sub {
  display: block;
  margin-top: 16rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}

/* 卡片 */
.ld-card {
  margin-top: $ld-gap-block;
  padding: $ld-pad-card;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
  box-sizing: border-box;
}
.ld-card-title {
  display: block;
  color: $ld-text;
  font-size: $ld-font-heading;
  font-weight: 700;
}
.ld-card-sub {
  display: block;
  margin-top: 8rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}

/* 功能入口:微信式分组单元格 */
.ld-group {
  margin-top: $ld-gap-block;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
  overflow: hidden;
}
.ld-group-title {
  display: block;
  padding: 32rpx 12rpx 16rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}
.ld-cell {
  width: 100%;
  min-height: 112rpx;
  display: flex;
  align-items: center;
  gap: 22rpx;
  margin: 0;
  padding: 22rpx 28rpx;
  box-sizing: border-box;
  background: $ld-card;
  text-align: left;
}
.ld-cell::after { border: 0; }
.ld-cell + .ld-cell { border-top: 2rpx solid $ld-line; }
.ld-cell:active { background: #f5f8f6; }
.ld-cell-icon {
  width: 76rpx;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18rpx;
  background: $ld-primary;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.ld-cell-icon.blue { background: #3d7bd0; }
.ld-cell-icon.orange { background: $ld-accent; }
.ld-cell-icon.purple { background: #7b6cd9; }
.ld-cell-icon.teal { background: #2b9a8f; }
.ld-cell-icon.red { background: #cf5a43; }
.ld-cell-icon.gold { background: #c08a2d; }
.ld-cell-icon.soft { background: $ld-primary-soft; color: $ld-primary; }
.ld-cell-icon.warn-soft { background: $ld-warning-soft; color: $ld-warning; }
.ld-cell-icon.danger-soft { background: $ld-danger-soft; color: $ld-danger; }
.ld-cell-body { flex: 1; min-width: 0; }
.ld-cell-title {
  display: block;
  color: $ld-text;
  font-size: 30rpx;
  font-weight: 500;
}
.ld-cell-sub {
  display: block;
  margin-top: 6rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}
.ld-cell-value {
  color: $ld-text-muted;
  font-size: $ld-font-caption;
  flex-shrink: 0;
}
.ld-cell-arrow {
  color: #c4d0ca;
  font-size: 40rpx;
  line-height: 1;
  flex-shrink: 0;
}

/* 功能入口行(独立卡片式,用于首页等) */
.ld-entry {
  width: 100%;
  min-height: 136rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin: 0 0 $ld-gap-block;
  padding: 26rpx 28rpx;
  box-sizing: border-box;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
  text-align: left;
}
.ld-entry::after { border: 0; }
.ld-entry-icon {
  width: 84rpx;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22rpx;
  background: $ld-primary-soft;
  color: $ld-primary;
  font-size: 36rpx;
  font-weight: 700;
  flex-shrink: 0;
}
.ld-entry-body { flex: 1; min-width: 0; }
.ld-entry-title {
  display: block;
  color: $ld-text;
  font-size: 30rpx;
  font-weight: 650;
}
.ld-entry-sub {
  display: block;
  margin-top: 8rpx;
  color: $ld-text-muted;
  font-size: $ld-font-caption;
}
.ld-entry-arrow {
  color: $ld-primary-border;
  font-size: 44rpx;
  line-height: 1;
  flex-shrink: 0;
}

/* 按钮 */
.ld-btn {
  width: 100%;
  height: 92rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  border-radius: $ld-radius-md;
  font-size: 30rpx;
  font-weight: 600;
  box-sizing: border-box;
}
.ld-btn::after { border: 0; }
.ld-btn-primary {
  background: $ld-gradient-primary;
  color: #ffffff;
  box-shadow: $ld-shadow-btn;
}
.ld-btn-primary[disabled] { background: #a9c4ba; color: #f2f7f5; box-shadow: none; }
.ld-btn-secondary {
  background: $ld-card;
  color: $ld-primary;
  border: 2rpx solid $ld-primary-border;
}
.ld-btn-secondary::after { border: 2rpx solid $ld-primary-border; border-radius: $ld-radius-md; }
.ld-btn-ghost {
  background: transparent;
  color: $ld-text-secondary;
  border: 2rpx solid $ld-line-strong;
}
.ld-btn-ghost::after { border: 2rpx solid $ld-line-strong; border-radius: $ld-radius-md; }
.ld-btn-danger {
  background: $ld-danger-soft;
  color: $ld-danger;
}

/* 表单 */
.ld-field { margin-top: $ld-gap-block; }
.ld-field-label {
  display: block;
  color: $ld-text-secondary;
  font-size: $ld-font-caption;
  font-weight: 600;
}
.ld-input {
  width: 100%;
  height: 92rpx;
  margin-top: 14rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
  border: 2rpx solid $ld-line-strong;
  border-radius: $ld-radius-md;
  background: #fbfdfc;
  color: $ld-text;
  font-size: 30rpx;
}

/* 页签 */
.ld-tabs {
  display: flex;
  gap: 12rpx;
  margin-top: $ld-gap-block;
  padding: 8rpx;
  border-radius: $ld-radius-md;
  background: #e9efec;
}
.ld-tab {
  flex: 1;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10rpx;
  color: $ld-text-secondary;
  font-size: $ld-font-caption;
  font-weight: 600;
}
.ld-tab.active {
  background: $ld-card;
  color: $ld-primary;
  box-shadow: 0 2rpx 8rpx rgba(21, 54, 43, 0.08);
}

/* 空态与状态 */
.ld-empty {
  padding: 72rpx 32rpx;
  border-radius: $ld-radius-lg;
  background: $ld-card;
  box-shadow: $ld-shadow-card;
  color: $ld-text-muted;
  font-size: $ld-font-body;
  text-align: center;
}
.ld-empty-main { display: block; color: $ld-text-secondary; font-size: 30rpx; font-weight: 600; }
.ld-empty-sub { display: block; margin-top: 12rpx; color: $ld-text-muted; font-size: $ld-font-caption; }
.ld-loading {
  padding: 72rpx 32rpx;
  color: $ld-text-muted;
  font-size: $ld-font-body;
  text-align: center;
}
.ld-error-text {
  display: block;
  min-height: 40rpx;
  color: $ld-danger;
  font-size: $ld-font-caption;
  line-height: 40rpx;
  word-break: break-word;
}

/* 徽标 */
.ld-badge {
  display: inline-flex;
  align-items: center;
  height: 44rpx;
  padding: 0 18rpx;
  border-radius: $ld-radius-pill;
  background: $ld-primary-soft;
  color: $ld-primary;
  font-size: $ld-font-mini;
  font-weight: 600;
}
.ld-badge.warn { background: $ld-warning-soft; color: $ld-warning; }
.ld-badge.danger { background: $ld-danger-soft; color: $ld-danger; }

/* 底部导航页:预留固定导航高度 */
.has-tabbar { padding-bottom: 176rpx !important; }

/* ===== 旧类名兼容层:未精修页面自动获得统一视觉 ===== */
/* 页面壳与标题 */
.page-shell {
  min-height: 100vh;
  padding: 32rpx !important;
  box-sizing: border-box !important;
  background: $ld-bg !important;
}
/* #ifdef H5 */
.page-shell { min-height: calc(100vh - 44px) !important; }
/* #endif */
.page-heading { margin: 0 4rpx 8rpx; }
.heading-title { color: $ld-text !important; font-size: $ld-font-title !important; font-weight: 700 !important; }
.heading-rule { width: 72rpx !important; height: 8rpx !important; margin-top: 18rpx !important; border-radius: 4rpx !important; background: $ld-accent !important; }
.heading-sub, .section-sub { color: $ld-text-muted !important; font-size: $ld-font-caption !important; }

/* 顶栏 */
.top-bar {
  background: $ld-card !important;
  border: 0 !important;
  border-radius: $ld-radius-lg !important;
  box-shadow: $ld-shadow-card !important;
  padding: 24rpx 28rpx !important;
  margin-bottom: 8rpx;
}
.brand-name { color: $ld-text !important; font-size: 30rpx !important; font-weight: 700 !important; }
.account-text { color: $ld-text-muted !important; }
.logout-button {
  width: auto !important;
  height: 64rpx !important;
  margin: 0 !important;
  padding: 0 26rpx !important;
  border-radius: $ld-radius-pill !important;
  background: $ld-danger-soft !important;
  color: $ld-danger !important;
  font-size: $ld-font-caption !important;
}
.logout-button::after { border: 0 !important; }

/* 区块与卡片 */
.band, .section-band, .summary, .form-band, .warning-band, .record-band, .list-band {
  border: 0 !important;
  border-radius: $ld-radius-lg !important;
  box-shadow: $ld-shadow-card !important;
}
.band, .form-band, .record-band, .list-band { background: $ld-card !important; padding: $ld-pad-card !important; margin-top: $ld-gap-block !important; }
.section-band { margin-top: $ld-gap-block !important; }
.section-title, .card-title { color: $ld-text !important; font-size: $ld-font-heading !important; font-weight: 700 !important; }
.warning-band {
  background: $ld-warning-soft !important;
  box-shadow: none !important;
}

/* 表单 */
.field-group, .field { scroll-margin: 0; }
.field-label, .form-label { color: $ld-text-secondary !important; font-size: $ld-font-caption !important; font-weight: 600 !important; }
.field-input, .input-field {
  border: 2rpx solid $ld-line-strong !important;
  border-radius: $ld-radius-md !important;
  background: #fbfdfc !important;
  height: 92rpx !important;
  color: $ld-text !important;
  font-size: 30rpx !important;
}

/* 按钮 */
.submit-button, .primary-action {
  background: $ld-gradient-primary !important;
  border-radius: $ld-radius-md !important;
  color: #ffffff !important;
  box-shadow: $ld-shadow-btn !important;
}
.submit-button[disabled] { background: #a9c4ba !important; box-shadow: none !important; }
.secondary-button { background: $ld-card !important; color: $ld-primary !important; border-radius: $ld-radius-md !important; }
.secondary-button::after { border: 2rpx solid $ld-primary-border !important; border-radius: $ld-radius-md !important; }
.outline-button { background: transparent !important; color: $ld-text-secondary !important; }
.outline-button::after { border: 2rpx solid $ld-line-strong !important; border-radius: $ld-radius-md !important; }
.scan-button { background: $ld-card !important; color: $ld-primary !important; border-radius: $ld-radius-md !important; }
.scan-button::after { border: 2rpx solid $ld-primary-border !important; border-radius: $ld-radius-md !important; }
.wechat-login-button { background: #07c160 !important; border-radius: $ld-radius-md !important; }

/* 页签与模式切换 */
.mode-switch, .tab-bar {
  border: 0 !important;
  padding: 8rpx !important;
  border-radius: $ld-radius-md !important;
  background: #e9efec !important;
}
.mode-button, .tab-button {
  height: 68rpx !important;
  border-radius: 10rpx !important;
  background: transparent !important;
  color: $ld-text-secondary !important;
  font-size: $ld-font-caption !important;
  font-weight: 600 !important;
}
.mode-button::after, .tab-button::after { border: 0 !important; }
.mode-button.active, .tab-button.active {
  background: $ld-card !important;
  color: $ld-primary !important;
  box-shadow: 0 2rpx 8rpx rgba(21, 54, 43, 0.08) !important;
}

/* 状态与空态 */
.state-text, .state-view, .loading-state, .disabled-state, .list-footer {
  color: $ld-text-muted !important;
  font-size: $ld-font-body !important;
}
.state-title { color: $ld-text-secondary !important; font-size: 30rpx !important; font-weight: 600 !important; }
.disabled-state, .unavailable .state-title { color: $ld-danger !important; }
.error-message, .error { color: $ld-danger !important; }

/* 通用行卡(列表页的行容器统一为卡片观感) */
.record-row, .row-card, .item-card, .info-row {
  border-radius: $ld-radius-lg !important;
  background: $ld-card !important;
  box-shadow: $ld-shadow-card !important;
}
</style>