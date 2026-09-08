const REPO = "DARKPIX404/SelfFlow";
const FALLBACK_URL = `https://github.com/${REPO}/releases/latest`;

const downloadBtn = document.getElementById("download-btn");
const releaseInfo = document.getElementById("release-info");
const releaseVersion = document.getElementById("release-version");
const releaseDate = document.getElementById("release-date");

function formatDate(iso) {
  return new Date(iso).toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

async function loadLatestRelease() {
  // /releases (а не /releases/latest), потому что сборки помечены prerelease,
  // а эндпоинт latest игнорирует их.
  const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=1`);
  if (!response.ok) {
    throw new Error(`GitHub API вернул ${response.status}`);
  }
  const releases = await response.json();
  const latest = releases[0];
  if (!latest) {
    throw new Error("В репозитории нет релизов");
  }
  const apk = latest.assets.find((asset) => asset.name.endsWith(".apk"));
  if (!apk) {
    throw new Error(`В релизе ${latest.tag_name} нет APK-ассета`);
  }
  return { tag: latest.tag_name, date: latest.published_at, url: apk.browser_download_url };
}

loadLatestRelease()
  .then(({ tag, date, url }) => {
    downloadBtn.href = url;
    releaseVersion.textContent = tag;
    releaseDate.textContent = formatDate(date);
    releaseInfo.hidden = false;
  })
  .catch((error) => {
    // Без доступа к API (лимит, приватный репозиторий, сеть) ведём на страницу релизов.
    downloadBtn.href = FALLBACK_URL;
    console.warn("Не удалось получить последний релиз, используется ссылка на список релизов:", error);
  });
