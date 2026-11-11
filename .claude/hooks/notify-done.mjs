#!/usr/bin/env node
// Stop hook: show a desktop notification when Claude finishes a response.
// Windows: toast (signed-in user only; Windows Home can't message other users).
// macOS: osascript. Linux: notify-send if installed. Otherwise does nothing.
// Opt out per machine with COLPOOL_NO_NOTIFY=1. Never blocks or fails the session.
import fs from "node:fs";
import { spawn } from "node:child_process";

if (process.env.COLPOOL_NO_NOTIFY === "1") process.exit(0);

let input = {};
try {
  input = JSON.parse(fs.readFileSync(0, "utf8"));
} catch {}

const title = "Claude finished (ColPool)";
const raw = typeof input.last_assistant_message === "string" ? input.last_assistant_message : "";
const firstLine = raw.split("\n").map((s) => s.replace(/[*_`#>]/g, "").trim()).find(Boolean) || "Ready for your review.";
const body = firstLine.length > 180 ? firstLine.slice(0, 177) + "…" : firstLine;

function run(cmd, args) {
  try {
    const child = spawn(cmd, args, { detached: true, stdio: "ignore", windowsHide: true });
    child.on("error", () => {});
    child.unref();
  } catch {}
}

const esc = (s) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/'/g, "''");

if (process.platform === "win32") {
  const appId = "{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\\WindowsPowerShell\\v1.0\\powershell.exe";
  const ps = `
[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime] | Out-Null
$xml = New-Object Windows.Data.Xml.Dom.XmlDocument
$xml.LoadXml('<toast><visual><binding template="ToastGeneric"><text>${esc(title)}</text><text>${esc(body)}</text></binding></visual></toast>')
[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('${appId}').Show([Windows.UI.Notifications.ToastNotification]::new($xml))
`;
  const encoded = Buffer.from(ps, "utf16le").toString("base64");
  run("powershell.exe", ["-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded]);
} else if (process.platform === "darwin") {
  const q = (s) => s.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
  run("osascript", ["-e", `display notification "${q(body)}" with title "${q(title)}"`]);
} else {
  run("notify-send", [title, body]);
}
process.exit(0);
