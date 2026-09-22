import pathlib
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[2]


class UiLocalizationTest(unittest.TestCase):
    def strings(self, folder):
        root = ET.parse(ROOT / "app" / "src" / "main" / "res" / folder / "strings.xml").getroot()
        return {node.attrib["name"]: "".join(node.itertext()) for node in root.findall("string")}

    def test_english_has_every_default_resource(self):
        default = self.strings("values")
        english = self.strings("values-en")
        self.assertEqual(set(default), set(english))
        self.assertTrue(all(value.strip() for value in english.values()))

    def test_critical_english_workflows_are_not_french_fallbacks(self):
        english = self.strings("values-en")
        expected = {
            "models_tab_explore": "Explore",
            "training_start": "Train exported batch",
            "training_phase_evaluating": "Evaluation",
            "qualification_training": "Training qualified",
            "export_format_coco": "COCO — boxes + masks",
        }
        for key, value in expected.items():
            self.assertEqual(value, english[key])


if __name__ == "__main__":
    unittest.main()
