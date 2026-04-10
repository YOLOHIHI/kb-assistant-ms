// 文件工具函数

const ALLOWED_IMAGE_TYPES = ['image/png', 'image/jpg', 'image/jpeg', 'image/webp', 'image/gif']
const MAX_AVATAR_SIZE = 700000

export interface AvatarValidationResult {
  valid: boolean
  error?: string
  dataUrl?: string
}

export function validateImageType(file: File): boolean {
  return ALLOWED_IMAGE_TYPES.includes(file.type)
}

export async function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result)
      } else {
        reject(new Error('Failed to read file'))
      }
    }
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

export async function validateAndConvertAvatar(file: File): Promise<AvatarValidationResult> {
  if (!validateImageType(file)) {
    return {
      valid: false,
      error: '仅支持 PNG、JPG、JPEG、WebP、GIF 格式的图片',
    }
  }

  try {
    const dataUrl = await fileToDataUrl(file)
    
    if (dataUrl.length > MAX_AVATAR_SIZE) {
      return {
        valid: false,
        error: '图片文件过大，请选择较小的图片',
      }
    }

    return {
      valid: true,
      dataUrl,
    }
  } catch {
    return {
      valid: false,
      error: '读取图片失败',
    }
  }
}
