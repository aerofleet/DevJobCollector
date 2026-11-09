"""Create a synthetic long HWPX from a local public ALIO corpus fixture."""
import copy
import json
import os
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

directory = Path(tempfile.gettempdir()) / "djc-hwp-corpus"
manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8-sig"))
source = Path(next(item["path"] for item in manifest if item["name"].lower().endswith(".hwpx")))
destination = directory / "synthetic-60-pages.hwpx"
with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED) as result:
    for entry in original.infolist():
        data = original.read(entry.filename)
        if entry.filename == "Contents/section0.xml":
            section = ET.fromstring(data)
            paragraph = next(item for item in list(section)[1:] if item.tag.endswith("}p") and len(list(item.iter())) < 10)
            for index in range(60):
                clone = copy.deepcopy(paragraph)
                clone.set("id", str(1000000 + index))
                clone.set("pageBreak", "1")
                section.append(clone)
            data = ET.tostring(section, encoding="utf-8", xml_declaration=True)
        result.writestr(entry, data)
print(destination)
