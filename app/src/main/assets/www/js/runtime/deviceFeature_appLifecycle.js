/**
 * Device Feature: App Lifecycle
 * Manages application lifecycle (onCreate, onDestroy) and app info.
 */
const DeviceFeatureAppLifecycle = (() => {
  let _appExports = null;
  let _onAppTerminate = null;
  let _appData = null;

  function setAppExports(exports) {
    _appExports = exports;
  }

  function setAppData(appData) {
    _appData = appData;
  }

  function setOnAppTerminate(cb) {
    _onAppTerminate = cb;
  }

  function getAppExports() {
    return _appExports;
  }

  function getAppInfo() {
    return {
      appName: _appData?.manifest?.appName || _appData?.bundleName || '',
      versionName: _appData?.version || '',
      versionCode: _appData?.versionCode || 1,
      bundleName: _appData?.bundleName || '',
    };
  }

  function terminate() {
    if (_onAppTerminate) _onAppTerminate();
  }

  /**
   * Call app onCreate lifecycle
   */
  function callOnCreate() {
    if (_appExports && _appExports.onCreate) {
      // 与 callOnDestroy 对称：onCreate 抛错不能让调用方（app.js loadAppFile）的
      // 外层 try/catch 吞掉后续流程，否则入口页导航被跳过 → NO_ENTRY_PAGE 黑屏。
      // 个别 Lite 工程 onCreate 里会调用自身 bundle 缺失的方法（如 store.load）。
      try { _appExports.onCreate(); } catch (e) { console.error('[AppLifecycle] onCreate error:', e); }
    }
  }

  /**
   * Call app onDestroy lifecycle
   */
  function callOnDestroy() {
    if (_appExports && _appExports.onDestroy) {
      try { _appExports.onDestroy(); } catch (e) { console.error('[AppLifecycle] onDestroy error:', e); }
    }
  }

  return {
    setAppExports, setAppData, setOnAppTerminate,
    getAppExports, getAppInfo, terminate,
    callOnCreate, callOnDestroy,
  };
})();
