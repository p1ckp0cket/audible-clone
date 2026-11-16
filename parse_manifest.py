#!/usr/bin/env python3
import xml.etree.ElementTree as ET
tree = ET.parse('AndroidManifest.xml')
root = tree.getroot()
print("Root tag:", root.tag)
for elem in root.iter():
    if 'package' in elem.attrib:
        print('Package:', elem.attrib['package'])
    if 'name' in elem.attrib:
        print('Name:', elem.attrib['name'])
    if 'label' in elem.attrib:
        print('Label:', elem.attrib['label'])
    if 'icon' in elem.attrib:
        print('Icon:', elem.attrib['icon'])
    if 'theme' in elem.attrib:
        print('Theme:', elem.attrib['theme'])