import * as ImagePicker from "expo-image-picker";

import { createLocalAttachmentMetadata } from "./attachmentMetadata";
import type { LocalAttachmentMetadata } from "./types";

type PickedImageAsset = {
  uri?: string;
  fileName?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  width?: number | null;
  height?: number | null;
  assetId?: string | null;
};

export async function selectImageFromGallery(
  existing: LocalAttachmentMetadata[]
): Promise<LocalAttachmentMetadata | null> {
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ["images"],
    quality: 1,
    allowsMultipleSelection: false,
    exif: false,
    base64: false
  });

  if (result.canceled || !result.assets[0]) {
    return null;
  }

  return createAttachmentFromPickedAsset(result.assets[0], existing, "gallery");
}

export async function selectImageFromCamera(
  existing: LocalAttachmentMetadata[]
): Promise<LocalAttachmentMetadata | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    throw new Error("Camera permission is required to select a local preview.");
  }

  const result = await ImagePicker.launchCameraAsync({
    mediaTypes: ["images"],
    quality: 1,
    exif: false,
    base64: false
  });

  if (result.canceled || !result.assets[0]) {
    return null;
  }

  return createAttachmentFromPickedAsset(result.assets[0], existing, "camera");
}

export function createAttachmentFromPickedAsset(
  asset: PickedImageAsset,
  existing: LocalAttachmentMetadata[],
  source: "camera" | "gallery" | "web" = "gallery"
): LocalAttachmentMetadata {
  const uri = asset.uri?.trim();
  if (!uri) {
    throw new Error("Selected image did not include a local preview reference.");
  }

  const mimeType = asset.mimeType?.trim().toLowerCase();
  if (!mimeType) {
    throw new Error("Selected image did not include a declared MIME type.");
  }

  if (!Number.isFinite(asset.fileSize) || !asset.fileSize || asset.fileSize < 1) {
    throw new Error("Selected image did not include a declared file size.");
  }

  const widthPx = typeof asset.width === "number" ? asset.width : undefined;
  const heightPx = typeof asset.height === "number" ? asset.height : undefined;
  const originalFileName = normalizeFileName(asset.fileName, mimeType, source);

  return createLocalAttachmentMetadata(
    {
      id: localImageId(asset, uri, source),
      originalFileName,
      mimeType,
      sizeBytes: asset.fileSize,
      widthPx,
      heightPx,
      localReference: uri,
      localUri: uri
    },
    existing
  );
}

function localImageId(asset: PickedImageAsset, uri: string, source: string): string {
  const stablePart = asset.assetId || uri;
  let hash = 0;
  for (let index = 0; index < stablePart.length; index += 1) {
    hash = (hash * 31 + stablePart.charCodeAt(index)) >>> 0;
  }
  return `local-${source}-${hash.toString(16)}`;
}

function normalizeFileName(fileName: string | null | undefined, mimeType: string, source: string): string {
  const trimmed = fileName?.trim();
  if (trimmed) {
    return trimmed;
  }

  const extension = mimeType === "image/png" ? "png" : mimeType === "image/webp" ? "webp" : "jpg";
  return `${source}-image.${extension}`;
}
