"""Push the project page and gallery to Modrinth, and read moderation state.

The token is read from the MODRINTH_TOKEN environment variable and is never stored anywhere:
create a personal access token at https://modrinth.com/settings/pats (scopes: read/write project,
read thread) and set it for the current shell only.

    status                      project state + the moderation thread, newest message last
    body                        replace the project description with docs/modrinth/description.md
    gallery IMG TITLE DESC [--featured]
                                upload one gallery image

Resubmitting for review is deliberately not here: do it in the web UI, after the checklist in
docs/modrinth/gallery-and-reply.md is done.
"""
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

PROJECT = "KNXZi4yx"
API = "https://api.modrinth.com/v2"
BODY = Path(__file__).resolve().parent.parent / "docs/modrinth/description.md"


def call(method, path, data=None, content_type=None):
    token = os.environ.get("MODRINTH_TOKEN")
    if not token:
        sys.exit("Set MODRINTH_TOKEN first (https://modrinth.com/settings/pats).")
    req = urllib.request.Request(
        API + path,
        data=data,
        method=method,
        headers={
            "Authorization": token,
            "User-Agent": "Mateo-Costas/djbooth (modrinth_sync)",
            **({"Content-Type": content_type} if content_type else {}),
        },
    )
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} -> {e.code}: {e.read().decode('utf-8', 'replace')}")


def status():
    _, p = call("GET", f"/project/{PROJECT}")
    print(f"status={p['status']} requested={p.get('requested_status')} gallery={len(p['gallery'])}")
    _, t = call("GET", f"/thread/{p['thread_id']}")
    for m in t["messages"]:
        body = m["body"]
        kind = body.get("type")
        text = body.get("body", "")
        print(f"--- {m['created']} [{kind}] {body.get('new_status', '')}")
        if text:
            print(text)


def body():
    text = BODY.read_text(encoding="utf-8")
    payload = json.dumps({"body": text}).encode("utf-8")
    call("PATCH", f"/project/{PROJECT}", payload, "application/json")
    _, p = call("GET", f"/project/{PROJECT}")
    assert p["body"] == text, "body read back differs from what was written"
    print(f"body updated ({len(text)} chars), read back identical")


def gallery(img, title, desc, featured):
    path = Path(img)
    ext = path.suffix.lstrip(".").lower()
    if ext not in {"png", "jpg", "jpeg", "webp", "gif"}:
        sys.exit("gallery images must be png/jpg/webp/gif")
    query = urllib.parse.urlencode(
        {"ext": ext, "featured": str(featured).lower(), "title": title, "description": desc}
    )
    call("POST", f"/project/{PROJECT}/gallery?{query}", path.read_bytes(), f"image/{ext}")
    print(f"uploaded {path.name} as '{title}' (featured={featured})")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args[:1] == ["status"]:
        status()
    elif args[:1] == ["body"]:
        body()
    elif args[:1] == ["gallery"] and len(args) >= 4:
        gallery(args[1], args[2], args[3], "--featured" in args[4:])
    else:
        sys.exit(__doc__)
