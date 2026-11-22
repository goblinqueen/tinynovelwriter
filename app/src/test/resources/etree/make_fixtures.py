"""Makes the fixtures for ElementTreeTest with Python's real ElementTree.

Run from this folder with Python 3.12: python make_fixtures.py
- input.xml: written by ElementTree + xmlIndent the way novelWriter saves a project.
- edited.xml: input.xml after the edit ElementTreeTest.reindentMatchesFreshBuild repeats,
  written the way novelWriter would: it builds a fresh tree on every save (so no parsed
  whitespace survives) and runs xmlIndent on it.
Both use CRLF line endings, like files novelWriter writes on Windows.
"""
import xml.etree.ElementTree as ET

DECLARATION = "<?xml version='1.0' encoding='utf-8'?>"


def xmlIndent(xml):
    """Copy of novelwriter/common.py xmlIndent (novelWriter 26.3b1); that module needs Qt."""
    tree = xml.getroot() if isinstance(xml, ET.ElementTree) else xml
    if not isinstance(tree, ET.Element):
        return

    indentations = ["\n"]

    def indentChildren(elem, level):
        chLevel = level + 1
        try:
            chIndent = indentations[chLevel]
        except IndexError:
            chIndent = indentations[level] + "  "
            indentations.append(chIndent)

        if elem.text is None:
            elem.text = chIndent

        last = None
        for child in elem:
            if len(child):
                indentChildren(child, chLevel)
            if child.tail is None:
                child.tail = chIndent
                last = child

        if last is not None:
            last.tail = indentations[level]

    if len(tree):
        indentChildren(tree, 0)
    tree.tail = "\n"


def write(root, path):
    # As ProjectXMLWriter.write, with the declaration fixed instead of the locale's.
    with open(path, mode="w", encoding="utf-8", errors="xmlcharrefreplace", newline="\r\n") as f:
        f.write(DECLARATION + "\n")
        ET.ElementTree(root).write(f, encoding="unicode")


def item(parent, handle, layout, words, name):
    attrs = {"handle": handle, "parent": "aaaaaaaaaaaa0", "root": "aaaaaaaaaaaa0", "order": "0", "type": "FILE", "class": "NOVEL", "layout": layout}
    x = ET.SubElement(parent, "item", attrib=attrs)
    ET.SubElement(x, "meta", attrib={"expanded": "no", "heading": "H1", "charCount": str(words * 5), "wordCount": str(words), "paraCount": "1", "cursorPos": "0"})
    n = ET.SubElement(x, "name", attrib={"status": "s1", "import": "i1", "active": "yes"})
    n.text = name
    return x


root = ET.Element("novelWriterXML", attrib={"appVersion": "26.2.2", "fileVersion": "1.6"})
project = ET.SubElement(root, "project", attrib={"id": "x", "saveCount": "4"})
ET.SubElement(project, "name").text = "Проект & <тест> \"q\" 'a'"
ET.SubElement(project, "author")
settings = ET.SubElement(root, "settings")
replace = ET.SubElement(settings, "autoReplace")
ET.SubElement(replace, "entry", attrib={"key": "<a> & \"b\"\nc\td\r"}).text = "v > w"
ET.SubElement(settings, "empty").text = ""
content = ET.SubElement(root, "content", attrib={"items": "2", "novelWords": "10", "notesWords": "5"})
root_item = ET.SubElement(content, "item", attrib={"handle": "aaaaaaaaaaaa0", "parent": "None", "type": "ROOT", "class": "NOVEL"})
ET.SubElement(root_item, "meta", attrib={"expanded": "yes"})
ET.SubElement(root_item, "name", attrib={"status": "s1", "import": "i1"}).text = "Роман"
item(content, "aaaaaaaaaaaa1", "DOCUMENT", 10, "Глава 1")
xmlIndent(root)
write(root, "input.xml")

# Round trip: parsing and writing input.xml unchanged must give the same bytes. The parser
# drops the newline after the root; xmlIndent, which novelWriter runs on every save, puts it back.
with open("input.xml", "rb") as f:
    original = f.read()
tree = ET.parse("input.xml")
xmlIndent(tree.getroot())
write(tree.getroot(), "roundtrip.tmp")
with open("roundtrip.tmp", "rb") as f:
    assert f.read() == original, "input.xml doesn't round-trip in Python"

# The edit ElementTreeTest repeats: append an item, insert one before the first
# document, give a leaf element a child; then indent and write.
root = tree.getroot()
content = root.find("content")
item(content, "aaaaaaaaaaaa3", "NOTE", 3, "Новая заметка")
inserted = ET.Element("item", attrib={"handle": "aaaaaaaaaaaa2", "type": "FOLDER"})
ET.SubElement(inserted, "meta", attrib={"expanded": "no"})
ET.SubElement(inserted, "name").text = "Папка"
content.insert(1, inserted)
ET.SubElement(root.find("project").find("author"), "first").text = "А"


def fresh(elem):
    """A new tree with the same tags, attributes and leaf texts, like ProjectXMLWriter builds."""
    copy = ET.Element(elem.tag, attrib=dict(elem.attrib))
    if len(elem) == 0:
        copy.text = elem.text
    for child in elem:
        copy.append(fresh(child))
    return copy


root = fresh(root)
xmlIndent(root)
write(root, "edited.xml")

import os
os.remove("roundtrip.tmp")
print("ok")
