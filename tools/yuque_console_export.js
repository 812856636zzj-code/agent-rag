(() => {
  const pick = (selectors) => selectors.map((s) => document.querySelector(s)).find(Boolean);
  const title =
    pick(["h1", ".ne-title-text", ".doc-title", "[data-testid='doc-title']"])?.innerText?.trim() ||
    document.title.replace(/ - 语雀.*/, "").trim() ||
    "yuque-doc";

  const content =
    pick([
      ".lake-engine-view",
      ".lake-content",
      ".ne-viewer-body",
      ".doc-reader",
      "article",
      "main",
    ]) || document.body;

  const cleanName = title.replace(/[\\/:*?"<>|]/g, "_").slice(0, 80) || "yuque-doc";

  const blockTags = new Set([
    "ADDRESS",
    "ARTICLE",
    "ASIDE",
    "BLOCKQUOTE",
    "BR",
    "DIV",
    "DL",
    "FIGCAPTION",
    "FIGURE",
    "FOOTER",
    "H1",
    "H2",
    "H3",
    "H4",
    "H5",
    "H6",
    "HEADER",
    "HR",
    "LI",
    "MAIN",
    "OL",
    "P",
    "PRE",
    "SECTION",
    "TABLE",
    "TR",
    "UL",
  ]);

  const escapeText = (text) => text.replace(/\u00a0/g, " ").replace(/[ \t]+\n/g, "\n");

  const tableToMarkdown = (table) => {
    const rows = [...table.querySelectorAll("tr")]
      .map((tr) => [...tr.children].map((td) => escapeText(td.innerText.trim().replace(/\s+/g, " "))))
      .filter((row) => row.length);
    if (!rows.length) return "";
    const header = rows[0];
    const divider = header.map(() => "---");
    return [header, divider, ...rows.slice(1)].map((row) => `| ${row.join(" | ")} |`).join("\n");
  };

  const nodeToMarkdown = (node) => {
    if (node.nodeType === Node.TEXT_NODE) return escapeText(node.nodeValue || "");
    if (node.nodeType !== Node.ELEMENT_NODE) return "";

    const tag = node.tagName;
    if (["SCRIPT", "STYLE", "NOSCRIPT", "SVG", "BUTTON"].includes(tag)) return "";
    if (tag === "IMG") return node.alt ? `![${node.alt}](${node.src})` : (node.src ? `![](${node.src})` : "");
    if (tag === "A") {
      const text = [...node.childNodes].map(nodeToMarkdown).join("").trim() || node.href;
      return node.href ? `[${text}](${node.href})` : text;
    }
    if (tag === "PRE") return `\n\n\`\`\`\n${node.innerText.replace(/\n+$/, "")}\n\`\`\`\n\n`;
    if (tag === "CODE") return `\`${node.innerText.trim()}\``;
    if (tag === "TABLE") return `\n\n${tableToMarkdown(node)}\n\n`;
    if (/^H[1-6]$/.test(tag)) return `\n\n${"#".repeat(Number(tag[1]))} ${node.innerText.trim()}\n\n`;
    if (tag === "LI") return `\n- ${[...node.childNodes].map(nodeToMarkdown).join("").trim()}`;

    const text = [...node.childNodes].map(nodeToMarkdown).join("");
    return blockTags.has(tag) ? `\n${text}\n` : text;
  };

  const markdown = `# ${title}\n\n${nodeToMarkdown(content)
    .replace(/\n{3,}/g, "\n\n")
    .replace(/[ \t]{2,}/g, " ")
    .trim()}\n`;

  const blob = new Blob([markdown], { type: "text/markdown;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${cleanName}.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);

  console.log(`Exported ${cleanName}.md`);
})();
