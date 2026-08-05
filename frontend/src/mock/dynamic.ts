/**
 * Mock 数据 - 沙箱动态分析（V5.2）
 * 对应后端 analyze-service DynamicAnalysisController
 */
import type {
  DynamicAnalysisTask,
  DynamicReport,
  ProcessTreeNode,
  NetworkConnection,
  FileOperation,
  AttackTechniqueMapping,
  DynamicIocItem,
  StixObject,
} from '@/types';
import { DynamicTaskStatus } from '@/types';

/** Mock 进程树 */
export const mockProcessTree: ProcessTreeNode[] = [
  {
    pid: 1234,
    name: 'malware_sample.exe',
    parentPid: 0,
    imagePath: 'C:\\Users\\Public\\malware_sample.exe',
    commandLine: 'C:\\Users\\Public\\malware_sample.exe -silent',
    action: '创建进程',
    malicious: true,
    children: [
      {
        pid: 1456,
        name: 'cmd.exe',
        parentPid: 1234,
        imagePath: 'C:\\Windows\\System32\\cmd.exe',
        commandLine: 'cmd.exe /c powershell -nop -w hidden -enc SGVsbG8=',
        action: '执行命令',
        malicious: true,
        children: [
          {
            pid: 1789,
            name: 'powershell.exe',
            parentPid: 1456,
            imagePath: 'C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe',
            commandLine: 'powershell -nop -w hidden -enc SGVsbG8=',
            action: '下载脚本',
            malicious: true,
          },
        ],
      },
      {
        pid: 2012,
        name: 'rundll32.exe',
        parentPid: 1234,
        imagePath: 'C:\\Windows\\System32\\rundll32.exe',
        commandLine: 'rundll32.exe C:\\Users\\Public\\payload.dll,Start',
        action: '加载 DLL',
        malicious: true,
      },
    ],
  },
];

/** Mock 网络连接 */
export const mockNetworkConnections: NetworkConnection[] = [
  {
    dstIp: '45.155.205.233',
    dstPort: 443,
    dstDomain: 'malicious-update.example-evil.com',
    protocol: 'HTTPS',
    bytes: 15200,
    malicious: true,
  },
  {
    dstIp: '194.165.16.78',
    dstPort: 8080,
    protocol: 'TCP',
    bytes: 8420,
    malicious: true,
  },
  {
    dstIp: '8.8.8.8',
    dstPort: 53,
    protocol: 'DNS',
    bytes: 512,
    malicious: false,
  },
];

/** Mock 文件操作 */
export const mockFileOperations: FileOperation[] = [
  {
    type: 'create',
    path: 'C:\\Users\\Public\\payload.dll',
    processName: 'malware_sample.exe',
    malicious: true,
  },
  {
    type: 'write',
    path: 'C:\\Windows\\Temp\\~tmp4321.bat',
    processName: 'cmd.exe',
    malicious: true,
  },
  {
    type: 'delete',
    path: 'C:\\Users\\Public\\malware_sample.exe',
    processName: 'powershell.exe',
    malicious: true,
  },
  {
    type: 'read',
    path: 'C:\\Windows\\System32\\config\\SAM',
    processName: 'rundll32.exe',
    malicious: true,
  },
];

/** Mock ATT&CK 技术映射（动态分析报告产出，与 hunting 模块的 mockAttackTechniques 区分） */
export const mockDynamicAttackTechniques: AttackTechniqueMapping[] = [
  {
    techniqueId: 'T1059',
    tactic: 'execution',
    name: 'Command and Scripting Interpreter',
    description: '通过 cmd.exe / powershell.exe 执行编码命令，实现无文件执行',
  },
  {
    techniqueId: 'T1059.001',
    tactic: 'execution',
    name: 'PowerShell',
    description: '使用 PowerShell 执行隐藏窗口编码命令',
  },
  {
    techniqueId: 'T1129',
    tactic: 'execution',
    name: 'Shared Modules',
    description: '通过 rundll32.exe 加载恶意 DLL',
  },
  {
    techniqueId: 'T1071',
    tactic: 'command-and-control',
    name: 'Application Layer Protocol',
    description: '通过 HTTPS 与 C2 服务器通信',
  },
  {
    techniqueId: 'T1071.001',
    tactic: 'command-and-control',
    name: 'Web Protocols',
    description: '使用 HTTPS 协议回传数据',
  },
  {
    techniqueId: 'T1070',
    tactic: 'defense-evasion',
    name: 'Indicator Removal',
    description: '运行后删除自身可执行文件以消除痕迹',
  },
];

/** Mock IOC 列表 */
export const mockDynamicIocs: DynamicIocItem[] = [
  {
    type: 'ip',
    value: '45.155.205.233',
    source: 'network',
    description: 'C2 服务器 IP',
    techniqueId: 'T1071',
  },
  {
    type: 'domain',
    value: 'malicious-update.example-evil.com',
    source: 'network',
    description: 'C2 域名',
    techniqueId: 'T1071',
  },
  {
    type: 'ip',
    value: '194.165.16.78',
    source: 'network',
    description: '次要 C2 IP',
    techniqueId: 'T1071',
  },
  {
    type: 'path',
    value: 'C:\\Users\\Public\\payload.dll',
    source: 'malware_sample.exe',
    description: '释放的恶意 DLL',
    techniqueId: 'T1129',
  },
  {
    type: 'path',
    value: 'C:\\Windows\\Temp\\~tmp4321.bat',
    source: 'cmd.exe',
    description: '临时批处理脚本',
    techniqueId: 'T1059',
  },
];

/** Mock STIX 对象 */
export const mockStixObjects: StixObject[] = [
  {
    type: 'process',
    id: 'process--a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    name: 'malware_sample.exe',
    pid: 1234,
    created: '2026-07-26T08:00:00Z',
  },
  {
    type: 'process',
    id: 'process--b2c3d4e5-f6a7-8901-bcde-f12345678901',
    name: 'powershell.exe',
    pid: 1789,
    created: '2026-07-26T08:00:05Z',
  },
  {
    type: 'network-traffic',
    id: 'network-traffic--c3d4e5f6-a7b8-9012-cdef-123456789012',
    dst_ref: 'ipv4-addr--45.155.205.233',
    dst_port: 443,
    protocols: ['tcp', 'https'],
  },
];

/** Mock 动态分析任务列表 */
export const mockDynamicTasks: DynamicAnalysisTask[] = [
  {
    taskId: 'dyn-001',
    fileId: 1,
    cuckooTaskId: '1',
    status: DynamicTaskStatus.PARSED,
    processTree: mockProcessTree,
    networkConnections: mockNetworkConnections,
    fileOperations: mockFileOperations,
    attackTechniques: mockDynamicAttackTechniques,
    iocs: mockDynamicIocs,
    degraded: false,
    createTime: '2026-07-26T08:00:00Z',
    updateTime: '2026-07-26T08:02:00Z',
    parsedTime: '2026-07-26T08:02:00Z',
  },
  {
    taskId: 'dyn-002',
    fileId: 2,
    cuckooTaskId: '2',
    status: DynamicTaskStatus.COMPLETED,
    processTree: [],
    networkConnections: [],
    fileOperations: [],
    attackTechniques: [],
    iocs: [],
    degraded: false,
    createTime: '2026-07-26T08:05:00Z',
    updateTime: '2026-07-26T08:07:00Z',
  },
  {
    taskId: 'dyn-003',
    fileId: 3,
    cuckooTaskId: 'degraded-3',
    status: DynamicTaskStatus.DEGRADED,
    degraded: true,
    errorMessage: 'Cuckoo 沙箱不可用，已降级',
    processTree: [],
    networkConnections: [],
    fileOperations: [],
    attackTechniques: [],
    iocs: [],
    createTime: '2026-07-26T08:10:00Z',
    updateTime: '2026-07-26T08:10:00Z',
  },
];

/** Mock 动态分析报告 */
export const mockDynamicReport: DynamicReport = {
  taskId: 'dyn-001',
  fileId: 1,
  cuckooTaskId: '1',
  status: DynamicTaskStatus.PARSED,
  degraded: false,
  score: 8.2,
  summary:
    '样本通过 cmd.exe 调用 PowerShell 执行编码命令，释放恶意 DLL 并通过 rundll32 加载，建立 HTTPS C2 通道回传数据，运行后自删除以消除痕迹。映射 ATT&CK 6 项技术，涉及执行、命令控制、防御规避三个战术。',
  processTree: mockProcessTree,
  networkConnections: mockNetworkConnections,
  fileOperations: mockFileOperations,
  attackTechniques: mockDynamicAttackTechniques,
  iocs: mockDynamicIocs,
  stixObjects: mockStixObjects,
  createTime: '2026-07-26T08:00:00Z',
  parsedTime: '2026-07-26T08:02:00Z',
};

/**
 * 根据任务ID获取 Mock 任务
 */
export function getMockDynamicTaskById(taskId: string): DynamicAnalysisTask | undefined {
  return mockDynamicTasks.find((t) => t.taskId === taskId);
}

/**
 * 根据任务ID生成 Mock 报告
 * 若任务为降级状态，返回降级报告；否则返回完整报告
 */
export function getMockDynamicReport(taskId: string): DynamicReport {
  const task = getMockDynamicTaskById(taskId);
  if (task && task.degraded) {
    return {
      taskId: task.taskId,
      fileId: task.fileId,
      cuckooTaskId: task.cuckooTaskId,
      status: task.status,
      degraded: true,
      errorMessage: task.errorMessage ?? 'Cuckoo 沙箱不可用，已降级',
      processTree: [],
      networkConnections: [],
      fileOperations: [],
      attackTechniques: [],
      iocs: [],
      stixObjects: [],
      createTime: task.createTime,
    };
  }
  return { ...mockDynamicReport, taskId, fileId: task?.fileId ?? 1 };
}

/**
 * 模拟提交动态分析任务，返回 taskId
 */
export function mockSubmitDynamicAnalysis(fileId: number): string {
  // 模拟降级：fileId 为 3 的倍数时降级
  const degraded = fileId % 3 === 0;
  const taskId = `dyn-${Date.now().toString(36)}`;
  const task: DynamicAnalysisTask = {
    taskId,
    fileId,
    cuckooTaskId: degraded ? `degraded-${fileId}` : String(fileId),
    status: degraded ? DynamicTaskStatus.DEGRADED : DynamicTaskStatus.SUBMITTED,
    degraded,
    errorMessage: degraded ? 'Cuckoo 沙箱不可用，已降级' : undefined,
    processTree: [],
    networkConnections: [],
    fileOperations: [],
    attackTechniques: [],
    iocs: [],
    createTime: new Date().toISOString(),
    updateTime: new Date().toISOString(),
  };
  mockDynamicTasks.unshift(task);
  return taskId;
}

export default {
  mockProcessTree,
  mockNetworkConnections,
  mockFileOperations,
  mockDynamicAttackTechniques,
  mockDynamicIocs,
  mockStixObjects,
  mockDynamicTasks,
  mockDynamicReport,
  getMockDynamicTaskById,
  getMockDynamicReport,
  mockSubmitDynamicAnalysis,
};
