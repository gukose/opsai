import * as ImagePicker from "expo-image-picker";
import * as FileSystem from "expo-file-system/legacy";
import * as ImageManipulator from "expo-image-manipulator";

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
    quality: 0.82,
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
    quality: 0.82,
    exif: false,
    base64: false
  });

  if (result.canceled || !result.assets[0]) {
    return null;
  }

  return createAttachmentFromPickedAsset(result.assets[0], existing, "camera");
}

export async function createAttachmentFromPickedAsset(
  asset: PickedImageAsset,
  existing: LocalAttachmentMetadata[],
  source: "camera" | "gallery" | "web" = "gallery"
): Promise<LocalAttachmentMetadata> {
  const uri = asset.uri?.trim();
  if (!uri) {
    throw new Error("Selected image did not include a local preview reference.");
  }

  const inspected = await normalizeCameraAsset(uri, asset, source);

  return createLocalAttachmentMetadata(
    {
      id: localImageId(asset, uri, source),
      originalFileName: inspected.fileName,
      mimeType: inspected.mimeType,
      sizeBytes: inspected.sizeBytes,
      widthPx: inspected.widthPx,
      heightPx: inspected.heightPx,
      localReference: inspected.uri,
      localUri: inspected.uri
    },
    existing
  );
}

async function normalizeCameraAsset(
  uri: string,
  asset: PickedImageAsset,
  source: "camera" | "gallery" | "web"
): Promise<{ uri: string; mimeType: string; fileName: string; sizeBytes: number; widthPx?: number; heightPx?: number }> {
  const info = await FileSystem.getInfoAsync(uri);
  const sizeBytes = info.exists && typeof info.size === "number" ? info.size : asset.fileSize ?? 0;
  const declaredMime = asset.mimeType?.trim().toLowerCase() || "image/jpeg";
  const needsJpeg = declaredMime === "image/heic" || declaredMime === "image/heif" || !["image/jpeg", "image/png", "image/webp"].includes(declaredMime);
  if (sizeBytes < 1) {
    throw new Error("Selected image is empty or unavailable on this device.");
  }

  if (sizeBytes <= 10_000_000 && !needsJpeg) {
    return {
      uri,
      mimeType: declaredMime,
      fileName: normalizeFileName(asset.fileName, declaredMime, source),
      sizeBytes,
      widthPx: typeof asset.width === "number" ? asset.width : undefined,
      heightPx: typeof asset.height === "number" ? asset.height : undefined
    };
  }

  const maxDimension = Math.max(asset.width ?? 0, asset.height ?? 0);
  const actions = maxDimension > 2048
    ? [{ resize: asset.width && asset.width >= (asset.height ?? 0) ? { width: 2048 } : { height: 2048 } }]
    : [];
  const compressed = await ImageManipulator.manipulateAsync(uri, actions, {
    compress: 0.82,
    format: ImageManipulator.SaveFormat.JPEG
  });
  const compressedInfo = await FileSystem.getInfoAsync(compressed.uri);
  const compressedSize = compressedInfo.exists && typeof compressedInfo.size === "number" ? compressedInfo.size : 0;
  if (compressedSize < 1 || compressedSize > 10_000_000) {
    throw new Error("Selected image is larger than 10 MB after safe compression.");
  }
  return {
    uri: compressed.uri,
    mimeType: "image/jpeg",
    fileName: `${source}-image.jpg`,
    sizeBytes: compressedSize,
    widthPx: compressed.width,
    heightPx: compressed.height
  };
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
