"""REC-22: kapalı sözlük ve sabit şablonlar; kimlik, ağ, shell veya model yok."""
import json
import sys

VERSION = "assistant-v1"
ALGORITHM = "assistant-rules-v1"
FIELDS = {"contractVersion", "topic", "intent", "locale", "genreProvided", "missingFields",
          "minimum", "recommended", "compatibility"}
MISSING = {"GENRE", "HARDWARE_PROFILE_ID", "GAME_ID", "PLATFORM"}
INTENTS = {"GAME": {"SELECT_GAME"}, "HARDWARE": {"ASSESS_GAME"},
           "LTZ_USAGE": {"VIEW_HISTORY", "GIVE_FEEDBACK"}}
MESSAGES = {
    "MISSING_INPUT": ("Devam etmek için belirtilen alanları doldurun.",
                      "Provide the listed fields to continue."),
    "GAME_SELECTION": ("Katalogda seçtiğiniz türü filtreleyin; oyun detayındaki sistem gereksinimlerini kontrol edin. Bu bir oyun veya FPS garantisi değildir.",
                       "Filter the catalog by your selected genre and check the game requirements. This does not guarantee a game choice or FPS."),
    "HARDWARE_DEFICIT": ("Minimum gereksinim veya bileşen uyumsuzluğu tespit edildi. Donanım değerlendirmesindeki kanıtlı açıkları inceleyin; FPS tahmini yoktur.",
                         "A minimum requirement deficit or component incompatibility was found. Review the proven gaps in the hardware assessment; no FPS estimate is available."),
    "HARDWARE_UNKNOWN": ("Teknik kanıtlar tam sistem uygunluğunu doğrulamıyor. Minimum ve önerilen alanları ayrı inceleyin; oyun içinde ölçüm yapın. FPS tahmini yoktur.",
                         "Technical evidence does not confirm full system suitability. Review minimum and recommended checks separately and measure in game. No FPS estimate is available."),
    "HISTORY_GUIDE": ("Öneri geçmişini açın ve kendi öneri kaydınızı seçin. Bu yanıt geçmişinizi okumaz veya değiştirmez.",
                      "Open recommendation history and select your own run. This response does not read or change your history."),
    "FEEDBACK_GUIDE": ("Kendi öneri kaydınızdaki oyunu seçin. Güncel sürümle beğeni veya ilgilenmiyorum geri bildirimi gönderin. Bu yanıt geri bildirim göndermez.",
                       "Select a game in your own recommendation run. Submit like or not interested feedback with the current version. This response does not submit feedback.")}


def validate(data):
    if type(data) is not dict or set(data) != FIELDS:
        raise ValueError("schema")
    if data["contractVersion"] != VERSION or data["locale"] not in ("tr", "en"):
        raise ValueError("version")
    if data["topic"] not in INTENTS or data["intent"] not in INTENTS[data["topic"]]:
        raise ValueError("intent")
    if type(data["genreProvided"]) is not bool or type(data["missingFields"]) is not list:
        raise ValueError("types")
    missing = data["missingFields"]
    if len(missing) > 4 or any(type(item) is not str or item not in MISSING for item in missing):
        raise ValueError("missing")
    if len(set(missing)) != len(missing):
        raise ValueError("duplicate")
    if data["minimum"] not in ("MEETS", "BELOW", "UNKNOWN") or data["recommended"] not in ("MEETS", "BELOW", "UNKNOWN"):
        raise ValueError("requirements")
    if data["compatibility"] not in ("UNKNOWN", "INCOMPATIBLE"):
        raise ValueError("compatibility")
    expected = {"GENRE"} if data["topic"] == "GAME" and not data["genreProvided"] else set()
    if data["topic"] != "HARDWARE" and set(missing) != expected:
        raise ValueError("context")
    if data["topic"] == "HARDWARE" and "GENRE" in missing:
        raise ValueError("hardware")


def advise(data):
    validate(data)
    status = "GUIDANCE"
    if data["missingFields"]:
        template, steps, status = "MISSING_INPUT", [], "NEEDS_INPUT"
    elif data["topic"] == "GAME":
        template, steps = "GAME_SELECTION", ["OPEN_GAME_DISCOVERY", "FILTER_GENRE", "VERIFY_GAME_REQUIREMENTS"]
    elif data["topic"] == "HARDWARE":
        if data["minimum"] == "BELOW" or data["compatibility"] == "INCOMPATIBLE":
            template, steps = "HARDWARE_DEFICIT", ["REVIEW_MINIMUM_DEFICIT", "COMPARE_RECOMMENDED_LEVEL"]
        else:
            template, steps = "HARDWARE_UNKNOWN", ["CHECK_UNKNOWN_REQUIREMENTS", "COMPARE_RECOMMENDED_LEVEL", "MEASURE_IN_GAME"]
        status = "INSUFFICIENT_DATA"
    elif data["intent"] == "VIEW_HISTORY":
        template, steps = "HISTORY_GUIDE", ["OPEN_RECOMMENDATION_HISTORY"]
    else:
        template, steps = "FEEDBACK_GUIDE", ["OPEN_RECOMMENDATION_HISTORY", "SELECT_OWN_ITEM", "SEND_FEEDBACK"]
    return {"contractVersion": VERSION, "algorithmVersion": ALGORITHM, "topic": data["topic"],
            "intent": data["intent"], "locale": data["locale"], "status": status, "template": template,
            "message": MESSAGES[template][0 if data["locale"] == "tr" else 1],
            "steps": steps, "missingFields": data["missingFields"]}


def main():
    if sys.version_info < (3, 12):
        raise ValueError("runtime")
    raw = sys.stdin.buffer.read(8193)
    if len(raw) > 8192:
        raise ValueError("input_limit")
    data = json.loads(raw.decode("utf-8"))
    result = json.dumps(advise(data), ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    encoded = result.encode("utf-8")
    if len(encoded) > 16384:
        raise ValueError("output_limit")
    sys.stdout.buffer.write(encoded)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, TypeError, KeyError):
        sys.stderr.write("ASSISTANT_INVALID_INPUT")
        sys.exit(2)
