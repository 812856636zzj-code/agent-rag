#!/usr/bin/env python3
"""Extract readable Markdown-ish text from a saved Yuque HTML page.

Usage:
  python tools/yuque_extract_from_html.py input.html output.md
"""

from __future__ import annotations

import re
import sys
from html.parser import HTMLParser
from pathlib import Path


BLOCK_TAGS = {
    "article",
    "blockquote",
    "br",
    "div",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "li",
    "main",
    "p",
    "pre",
    "section",
    "table",
    "tr",
    "ul",
    "ol",
}
SKIP_TAGS = {"script", "style", "noscript", "svg"}


class YuqueHTMLParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.skip_depth = 0
        self.in_title = False
        self.in_pre = False
        self.title_parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in SKIP_TAGS:
            self.skip_depth += 1
            return
        if self.skip_depth:
            return
        if tag == "title":
            self.in_title = True
        if tag == "pre":
            self.in_pre = True
            self.parts.append("\n\n```\n")
        elif tag == "li":
            self.parts.append("\n- ")
        elif tag in BLOCK_TAGS:
            self.parts.append("\n")
        elif tag == "img":
            attrs_map = dict(attrs)
            src = attrs_map.get("src")
            alt = attrs_map.get("alt") or ""
            if src:
                self.parts.append(f"![{alt}]({src})")

    def handle_endtag(self, tag: str) -> None:
        if tag in SKIP_TAGS and self.skip_depth:
            self.skip_depth -= 1
            return
        if self.skip_depth:
            return
        if tag == "title":
            self.in_title = False
        if tag == "pre":
            self.in_pre = False
            self.parts.append("\n```\n\n")
        elif tag in BLOCK_TAGS:
            self.parts.append("\n")

    def handle_data(self, data: str) -> None:
        if self.skip_depth:
            return
        if self.in_title:
            self.title_parts.append(data)
        if self.in_pre:
            self.parts.append(data)
            return
        text = data.replace("\xa0", " ")
        if text.strip():
            self.parts.append(text)

    @property
    def title(self) -> str:
        raw = "".join(self.title_parts).strip()
        return re.sub(r"\s*-\s*语雀.*$", "", raw) or "yuque-doc"

    @property
    def markdown(self) -> str:
        body = "".join(self.parts)
        body = re.sub(r"[ \t]{2,}", " ", body)
        body = re.sub(r"\n[ \t]+", "\n", body)
        body = re.sub(r"\n{3,}", "\n\n", body).strip()
        return f"# {self.title}\n\n{body}\n"


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__.strip(), file=sys.stderr)
        return 2

    source = Path(sys.argv[1])
    target = Path(sys.argv[2])
    parser = YuqueHTMLParser()
    parser.feed(source.read_text(encoding="utf-8", errors="ignore"))
    target.write_text(parser.markdown, encoding="utf-8")
    print(f"Wrote {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
