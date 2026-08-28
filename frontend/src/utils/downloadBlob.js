// Authenticated PDF endpoints can't be hit with a plain <a href> (no Authorization
// header would go out), so downloads go through axios as a blob and get saved via a
// throwaway object URL instead.
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
