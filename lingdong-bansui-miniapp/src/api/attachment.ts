import { ApiError, apiUrl, request, type ApiErrorBody } from './http';
import { getStudentSession } from '@/session/student-session';

export interface TaskAttachment {
  id: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  contentUrl: string;
}

/** 使用 uni-app 独立上传能力发送任务打卡图片。 */
export function uploadTaskAttachment(
  filePath: string,
  onProgress?: (progress: number) => void
): Promise<TaskAttachment> {
  const session = getStudentSession();
  if (!session) return Promise.reject(new Error('学生登录状态已失效'));
  return new Promise((resolve, reject) => {
    const task = uni.uploadFile({
      url: apiUrl('/attachments/uploads'),
      filePath,
      name: 'file',
      formData: {
        moduleCode: 'LEARNING_TASK_CHECKIN',
        fileCategory: 'IMAGE'
      },
      header: { Authorization: `Bearer ${session.accessToken}` },
      success: (response) => {
        const body = parseBody(response.data);
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(body as TaskAttachment);
          return;
        }
        reject(new ApiError(response.statusCode, body as ApiErrorBody));
      },
      fail: () => reject(new Error('图片上传未能完成'))
    });
    task.onProgressUpdate((event) => onProgress?.(event.progress));
  });
}

export function deleteTaskAttachment(fileId: string): Promise<void> {
  const session = getStudentSession();
  if (!session) return Promise.reject(new Error('学生登录状态已失效'));
  return request<void>(`/attachments/${encodeURIComponent(fileId)}`, {
    method: 'DELETE',
    header: { Authorization: `Bearer ${session.accessToken}` }
  });
}

/** 下载到平台临时目录后预览，避免把访问令牌放入图片 URL。 */
export function downloadTaskAttachment(contentUrl: string): Promise<string> {
  const session = getStudentSession();
  if (!session) return Promise.reject(new Error('学生登录状态已失效'));
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: apiUrl(contentUrl),
      header: { Authorization: `Bearer ${session.accessToken}` },
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300) {
          resolve(response.tempFilePath);
          return;
        }
        reject(new Error('图片读取未能完成'));
      },
      fail: () => reject(new Error('图片读取未能完成'))
    });
  });
}

function parseBody(data: string): unknown {
  try {
    return JSON.parse(data) as unknown;
  } catch {
    return {};
  }
}
