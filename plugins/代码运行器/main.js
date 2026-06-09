// ===== 代码运行器插件 =====
// 橘瓣 QuickJS 沙箱 - 同步风格，无 async/await
// 使用 exec() 执行终端命令（自动注入运行时环境变量）

// 运行时目录
var RUNTIME = '/data/data/com.orangechat/files/runtime';

// Node工作目录
var NODE_DIR = '/data/data/com.orangechat/files/.code_runner/node';

// 临时文件目录
var TEMP_DIR = '/data/data/com.orangechat/files/.code_runner/temp';

// 运行时环境前缀（自动检测运行时是否可用）
var __envPrefix = '';

// 初始化运行时环境
function initRuntime() {
  try {
    var check = Bridge.executeCommand('[ -d ' + RUNTIME + '/bin ] && echo YES || echo NO');
    if (check.output && check.output.indexOf('YES') !== -1) {
      __envPrefix = 'export PATH=' + RUNTIME + '/bin:$PATH LD_LIBRARY_PATH=' + RUNTIME + '/lib HOME=' + RUNTIME + ' && ';
    }
  } catch (e) {
    // 运行时不可用，继续无前缀执行
  }
}

// 初始化（首次加载时检测）
initRuntime();

// 带环境变量注入的命令执行
function exec(command) {
  if (__envPrefix) {
    return Bridge.executeCommand(__envPrefix + command);
  }
  return Bridge.executeCommand(command);
}

// ===== 辅助函数 =====

function ensureDir(path) {
  exec('mkdir -p ' + path);
}

function randomMarker() {
  return 'SCRIPT_EOF_' + Math.floor(Math.random() * 99999999);
}

function ensureNodeDir() {
  ensureDir(NODE_DIR);
  var check = exec('[ -f ' + NODE_DIR + '/package.json ] && echo OK || echo NO');
  if (check.output.indexOf('OK') === -1) {
    exec('cd ' + NODE_DIR + ' && npm init -y');
  }
  return null;
}

// ===== 工具函数 =====

function run_python(params) {
  var script = params.script;
  if (!script || script.trim() === '') {
    return { success: false, error: '请提供Python代码' };
  }

  if (params.install_deps && params.install_deps.trim() !== '') {
    var installResult = exec('pip install ' + params.install_deps);
    if (installResult.exitCode !== 0) {
      return { success: false, error: '安装依赖失败: ' + installResult.output };
    }
  }

  ensureDir(TEMP_DIR);
  var marker = randomMarker();
  var tempFile = TEMP_DIR + '/temp_script_' + Math.floor(Math.random() * 999999) + '.py';
  var sq = String.fromCharCode(39);
  exec('cat > ' + tempFile + ' << ' + sq + marker + sq + '\n' + script + '\n' + marker);

  var result = exec('python3 ' + tempFile);

  exec('rm -f ' + tempFile);

  if (result.exitCode === 0) {
    return { success: true, output: result.output.trim() };
  } else {
    return { success: false, error: 'Python执行失败:\n' + result.output };
  }
}

function run_node(params) {
  var script = params.script;
  if (!script || script.trim() === '') {
    return { success: false, error: '请提供JavaScript代码' };
  }

  var nodeError = ensureNodeDir();
  if (nodeError) return { success: false, error: nodeError };

  if (params.install_deps && params.install_deps.trim() !== '') {
    var installResult = exec('cd ' + NODE_DIR + ' && npm install ' + params.install_deps);
    if (installResult.exitCode !== 0) {
      return { success: false, error: '安装npm依赖失败: ' + installResult.output };
    }
  }

  ensureDir(TEMP_DIR);
  var marker = randomMarker();
  var tempFile = TEMP_DIR + '/temp_script_' + Math.floor(Math.random() * 999999) + '.js';
  var sq = String.fromCharCode(39);
  exec('cat > ' + tempFile + ' << ' + sq + marker + sq + '\n' + script + '\n' + marker);

  var result = exec('cd ' + NODE_DIR + ' && NODE_PATH=' + NODE_DIR + '/node_modules node ' + tempFile);

  exec('rm -f ' + tempFile);

  if (result.exitCode === 0) {
    return { success: true, output: result.output.trim() };
  } else {
    return { success: false, error: 'Node.js执行失败:\n' + result.output };
  }
}

function install_python_packages(params) {
  var packages = params.packages;
  if (!packages || packages.trim() === '') {
    return { success: false, error: '请提供包名' };
  }
  var result = exec('pip install ' + packages);
  if (result.exitCode === 0) {
    return { success: true, output: result.output.trim() };
  } else {
    return { success: false, error: '安装失败: ' + result.output };
  }
}

function install_node_packages(params) {
  var packages = params.packages;
  if (!packages || packages.trim() === '') {
    return { success: false, error: '请提供包名' };
  }
  var nodeError = ensureNodeDir();
  if (nodeError) return { success: false, error: nodeError };
  var result = exec('cd ' + NODE_DIR + ' && npm install ' + packages);
  if (result.exitCode === 0) {
    return { success: true, output: result.output.trim() };
  } else {
    return { success: false, error: '安装失败: ' + result.output };
  }
}

// ===== 导出 =====
exports.run_python = run_python;
exports.run_node = run_node;
exports.install_python_packages = install_python_packages;
exports.install_node_packages = install_node_packages;