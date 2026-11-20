import type { PresignResponse } from "@/lib/types";

export const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];

/**
 * Uploads straight from the browser to S3 using the presigned URL from meme-service.
 * Requires the bucket CORS rule from the README (PUT from your site origin).
 */
export async function putToS3(presigned: PresignResponse, file: File, onProgress?: (pct: number) => void) {
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", presigned.uploadUrl);
    Object.entries(presigned.headers).forEach(([k, v]) => xhr.setRequestHeader(k, v));
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => (xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (${xhr.status})`)));
    xhr.onerror = () => reject(new Error("Upload failed. Check your connection."));
    xhr.send(file);
  });
  return presigned;
}

export function validateImage(file: File, maxMb: number): string | null {
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) return "Only JPG, PNG, WEBP or GIF images";
  if (file.size > maxMb * 1024 * 1024) return `Max ${maxMb} MB please`;
  return null;
}
