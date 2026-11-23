"""Standart kütüphaneli REC-22 kural/şema testleri; dış kaynak veya model gerektirmez."""
import importlib.util
import pathlib
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parents[2] / "main/resources/assistant/assistant_rules.py"
SPEC = importlib.util.spec_from_file_location("assistant_rules", SCRIPT)
RULES = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RULES)


def data(topic="GAME", intent="SELECT_GAME"):
    return {"contractVersion": "assistant-v1", "topic": topic, "intent": intent, "locale": "tr",
            "genreProvided": True, "missingFields": [], "minimum": "UNKNOWN",
            "recommended": "UNKNOWN", "compatibility": "UNKNOWN"}


class AssistantRulesTest(unittest.TestCase):
    def test_game_is_deterministic_and_does_not_claim_catalog_results(self):
        result = RULES.advise(data())
        self.assertEqual(result, RULES.advise(data()))
        self.assertEqual("GAME_SELECTION", result["template"])
        self.assertNotIn("items", result)

    def test_missing_genre_and_hardware_fields_are_explicit(self):
        request = data()
        request.update(genreProvided=False, missingFields=["GENRE"])
        self.assertEqual("NEEDS_INPUT", RULES.advise(request)["status"])
        request = data("HARDWARE", "ASSESS_GAME")
        request["missingFields"] = ["HARDWARE_PROFILE_ID", "GAME_ID", "PLATFORM"]
        self.assertEqual(request["missingFields"], RULES.advise(request)["missingFields"])

    def test_hardware_deficit_unknown_and_compatibility_never_invent_fps(self):
        request = data("HARDWARE", "ASSESS_GAME")
        self.assertEqual("HARDWARE_UNKNOWN", RULES.advise(request)["template"])
        request["minimum"] = "BELOW"
        self.assertEqual("HARDWARE_DEFICIT", RULES.advise(request)["template"])
        request.update(minimum="UNKNOWN", compatibility="INCOMPATIBLE")
        self.assertEqual("INSUFFICIENT_DATA", RULES.advise(request)["status"])
        self.assertNotIn("estimatedFps", RULES.advise(request))

    def test_usage_locales_and_versions_are_fixed(self):
        for intent in ("VIEW_HISTORY", "GIVE_FEEDBACK"):
            request = data("LTZ_USAGE", intent)
            tr = RULES.advise(request)
            request["locale"] = "en"
            en = RULES.advise(request)
            self.assertNotEqual(tr["message"], en["message"])
            self.assertEqual("assistant-rules-v1", en["algorithmVersion"])

    def test_unknown_fields_versions_and_wrong_types_are_rejected(self):
        for field, value in (("contractVersion", "v2"), ("topic", "CHAT"), ("intent", "SELECT_GAME;whoami"),
                             ("locale", "xx"), ("genreProvided", "true"), ("minimum", "FAST"),
                             ("recommended", None), ("compatibility", "SAFE"), ("missingFields", "GENRE")):
            request = data()
            request[field] = value
            with self.subTest(field=field), self.assertRaises((ValueError, TypeError)):
                RULES.advise(request)
        request = data()
        request["userId"] = "not-permitted"
        with self.assertRaises(ValueError):
            RULES.advise(request)

    def test_shell_and_private_field_names_never_become_instructions(self):
        for value in ("$(whoami)", "__import__('os')", "PRIVATE_LIBRARY", "GENRE\n", "https://example.invalid"):
            request = data()
            request["missingFields"] = [value]
            with self.assertRaises(ValueError):
                RULES.advise(request)

    def test_duplicate_or_inconsistent_missing_fields_are_rejected(self):
        for fields in (["GENRE", "GENRE"], ["GENRE"] * 5, [1], ["GAME_ID"]):
            request = data()
            request["missingFields"] = fields
            with self.assertRaises(ValueError):
                RULES.advise(request)


if __name__ == "__main__":
    unittest.main()
