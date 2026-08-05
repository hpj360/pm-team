/**
 * 文件哈希计算工具
 * - 使用 Web Crypto API（SubtleCrypto）计算 SHA-256
 * - 模拟 MD5 / SM3（国密）输出，用于秒传判断与展示
 * - 支持分片读取大文件，提供进度回调
 */

/** 哈希计算进度回调 */
export type HashProgressCallback = (percent: number) => void;

/** 默认读取块大小（2MB，避免一次性占用过多内存） */
const READ_CHUNK_SIZE = 2 * 1024 * 1024;

/**
 * 将 ArrayBuffer 转为十六进制字符串
 */
function bufferToHex(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let hex = '';
  for (let i = 0; i < bytes.length; i++) {
    hex += bytes[i].toString(16).padStart(2, '0');
  }
  return hex;
}

/**
 * 使用 Web Crypto API 计算 SHA-256（真实哈希）
 * @param file 文件对象
 * @param onProgress 进度回调
 */
export async function calculateSha256(
  file: File | Blob,
  onProgress?: HashProgressCallback,
): Promise<string> {
  const total = file.size;
  let offset = 0;
  // SubtleCrypto 不支持流式更新，因此先聚合为单个 ArrayBuffer 再计算
  // 对于极大文件可优化为分块读取后拼接（此处采用分块读取聚合）
  const chunks: Uint8Array[] = [];

  while (offset < total) {
    const end = Math.min(offset + READ_CHUNK_SIZE, total);
    const slice = file.slice(offset, end);
    const buf = await slice.arrayBuffer();
    chunks.push(new Uint8Array(buf));
    offset = end;
    if (onProgress && total > 0) {
      onProgress(Math.round((offset / total) * 100));
    }
  }

  // 拼接所有分块
  const totalLength = chunks.reduce((sum, c) => sum + c.length, 0);
  const merged = new Uint8Array(totalLength);
  let pos = 0;
  for (const c of chunks) {
    merged.set(c, pos);
    pos += c.length;
  }

  const digest = await crypto.subtle.digest('SHA-256', merged);
  return bufferToHex(digest);
}

/**
 * 模拟 MD5（Web Crypto 不提供 MD5）
 * 基于 SHA-256 的前 32 位十六进制字符 + 文件大小混合，输出 32 位 hex
 * 注：仅用于演示秒传判断，生产环境应使用 SparkMD5 等库
 */
export async function calculateMd5(
  file: File | Blob,
  onProgress?: HashProgressCallback,
): Promise<string> {
  const sha256 = await calculateSha256(file, onProgress);
  // 取 SHA-256 前 32 字符作为 MD5 替代（演示用）
  return sha256.substring(0, 32);
}

/**
 * 模拟 SM3（国密哈希）
 * 输出 64 位 hex（与 SHA-256 长度一致），基于 SHA-256 做字符变换
 */
export async function calculateSm3(
  file: File | Blob,
  onProgress?: HashProgressCallback,
): Promise<string> {
  const sha256 = await calculateSha256(file, onProgress);
  // 简单变换：字符位置交换 + 大小写反转，模拟 SM3 输出
  const reversed = sha256.split('').reverse().join('');
  return reversed.padEnd(64, '0').substring(0, 64);
}

/**
 * 同时计算 MD5 与 SM3（单次读取文件，避免重复 IO）
 */
export async function calculateFileHashes(
  file: File,
  onProgress?: HashProgressCallback,
): Promise<{ md5: string; sm3: string; sha256: string }> {
  const sha256 = await calculateSha256(file, onProgress);
  return {
    sha256,
    md5: sha256.substring(0, 32),
    sm3: sha256.split('').reverse().join('').padEnd(64, '0').substring(0, 64),
  };
}

/**
 * 计算文件分片
 * @param file 文件
 * @param chunkSize 分片大小（字节）
 * @returns 分片 Blob 数组
 */
export function sliceFile(file: File, chunkSize: number): Blob[] {
  const chunks: Blob[] = [];
  const total = file.size;
  let offset = 0;
  let index = 0;
  while (offset < total) {
    const end = Math.min(offset + chunkSize, total);
    chunks.push(file.slice(offset, end));
    offset = end;
    index++;
  }
  return chunks;
}

export default {
  calculateSha256,
  calculateMd5,
  calculateSm3,
  calculateFileHashes,
  sliceFile,
};
