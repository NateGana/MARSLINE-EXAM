# MARSLINE — Simple Presentation Guide (matches the generated code)

⚠️ **Before you present:** this code has not been compiled/run yet (see README section 9). Run all
5 commands on your Windows machine first, fix any errors, and only then say "PASS" for whatever
you actually see on your own screen.

For each task: which file to open, what section to point at, what it does in simple words, a short
script, and one likely question with a simple answer.

---

## TASK 1 — Message Channel
**File:** `Task1MessageChannel.java`
**Show this section:** the two `from(...)` routes near the top of `configure()` — `Task1SourceRoute` and `Task1TargetRoute`.
**Simple words:** "Source" pretends to be the booking website; it just drops the booking into a queue. "Target" pretends to be the backend; it only reads from that same queue. They never call each other.
**Script:** *"Dito po, ang Online Booking System namin, nagpapadala lang siya sa isang queue. Hindi niya alam kung sino ang bumabasa nito — basta may nakalagay siyang message doon. Yung Backend naman, doon din kumukuha. Kaya kahit magkaiba ang oras nila, hindi mawawala ang booking."*
**Likely Q:** "Why not just call the backend directly?"
**Simple answer:** "Kasi kung direkta, kailangan parehong online yung dalawa. Sa queue, pwede munang maghintay yung message hanggang free na yung backend."

---

## TASK 2 — Content-Based Router
**File:** `Task2ContentBasedRouter.java`
**Show this section:** the `.choice().when(header("isLocal").isEqualTo(true))...otherwise()` block, and just above it, the `Processor` that computes `isLocal` from the Metro Manila list.
**Simple words:** We check the destination against a fixed list of Metro Manila cities. If it's on the list, `isLocal = true` and it goes LOCAL; if not, `otherwise()` sends it PROVINCIAL — so nothing ever falls through with no destination.
**Script:** *"Binabasa po namin yung destination ng bawat booking. Kung kabilang sa Metro Manila list — LOCAL. Kung wala — PROVINCIAL, gamit yung otherwise() para siguradong walang mawawala."*
**Likely Q:** "What if the destination is spelled differently or empty?"
**Simple answer:** "Kung hindi ito exact match sa listahan namin, awtomatiko siyang PROVINCIAL — hindi siya nade-drop, dahil andiyan yung otherwise() branch."

---

## TASK 3 — Aggregator
**File:** `Task3Aggregator.java` (merge logic lives in `BookingAggregationStrategy.java`)
**Show this section:** the `.aggregate(header("bookingId"), new BookingAggregationStrategy()).completionSize(3).completionTimeout(3000)` block, and the `aggregate()` method inside `BookingAggregationStrategy.java`.
**Simple words:** Every part (BOOKING/PAYMENT/TRIP) carries the same `bookingId`. Camel waits and merges parts sharing that ID. If all 3 arrive, it's complete and forwarded. If not within 3 seconds, it's dropped from moving forward.
**Script:** *"Dito, hinihintay namin ang tatlong parte ng booking — galing sa magkakaibang 'systems.' Kapag kumpleto ang tatlo sa loob ng 3 seconds, pinagsasama at ipinapasa namin. Kung hindi kumpleto — gaya ng BKG-3003 na kulang sa TRIP part — hindi ito ipoproseso."*
**Likely Q:** "How does it know which parts belong together?"
**Simple answer:** "Sa `bookingId`. Lahat ng parte ng parehong booking, may parehong bookingId, kaya alam ni Camel kung sino kasama nino."

---

## TASK 4 — Message Translator
**File:** `Task4MessageTranslator.java` (fields defined in `LegacyTicket.java`)
**Show this section:** the `parseLegacyXml(...)` method (real DOM parsing) and, right after it's called, the `mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ticket)` line (Jackson converting to JSON).
**Simple words:** We read the actual XML tags one by one using Java's built-in XML reader, put the values into a plain Java object, then let Jackson turn that object into JSON text. It's real parsing, not just replacing `<` with `{`.
**Script:** *"Yung legacy ticketing machine namin, XML lang ang export niya. Binabasa namin ito gamit ang DocumentBuilder — totoong pagbasa ng bawat tag — tapos ginagawa naming JSON object gamit ang Jackson, para magamit ng bagong CRM."*
**Likely Q:** "Why not just use find-and-replace on the text?"
**Simple answer:** "Kasi delikado 'yon — pwedeng magkamali kung magkaiba ang pagkakasunod-sunod ng tags. Sa totoong parsing, sigurado kaming tama ang bawat field kahit magbago ang ayos."

---

## TASK 5 — Error Handling + Retry
**File:** `Task5ErrorChannelRetry.java`
**Show this section:** the `errorHandler(deadLetterChannel(...).maximumRedeliveries(2)...)` block, and the `Processor` in the main route that simulates backend failure/success per booking.
**Simple words:** Our fake "backend" is programmed to fail a set number of times per booking. Camel automatically retries up to 2 more times. If it works within those tries, great. If not, the message goes to a "parking lot" queue instead of disappearing.
**Script:** *"Dito, sina-simulate namin na paminsan-minsan nagfa-fail yung backend. Awtomatikong sinusubukan ulit ni Camel — hanggang dalawang beses pa. Kung gumana — tulad ng 5001 at 5002 — successful. Kung hindi talaga gumana — tulad ng 5003 — inilalagay namin sa 'parking lot' para hindi ito basta mawala."*
**Likely Q:** "What happens to a message in the parking lot — is it gone forever?"
**Simple answer:** "Hindi po — nandoon lang siya, naghihintay ng manual review. Hindi siya nabubura, kaya walang nawawalang booking."

---

## Full Short Presentation Script

1. **Intro:** "Good morning/afternoon Professor. We are Group MARSLINE, and today we will present our Midterm Lab Exam for ITP103."
2. **Stack:** "We used Apache Camel, ActiveMQ, Java 17, Maven, and Jackson to apply 5 Enterprise Integration Patterns."
3. **Architecture:** "Our 5 tasks form one pipeline, and all of them run through one command: mvn compile exec:java."
4–8. **Tasks 1–5:** use the scripts above, right after showing each live demo.
9. **Live demo:** run the 5 `mvn` commands, one at a time.
10. **Results:** state only what you actually saw on screen just now.
11. **Conclusion:** "Queues keep our systems independent, routing and merging logic live in the integration layer, and our error handling makes sure no booking is ever silently lost. Thank you."
12. **Q&A:** use the "Likely Q / Simple answer" pairs above.

## GitHub Upload Instructions

1. Create a new empty repository on GitHub (don't add a README there — you already have one).
2. In this project folder, open Command Prompt and run:
   ```
   git init
   git add .
   git commit -m "Initial commit - MARSLINE EIP project"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo-name>.git
   git push -u origin main
   ```
3. Double-check on GitHub.com that `pom.xml`, `README.md`, and all files under `src/main/java` uploaded correctly.
