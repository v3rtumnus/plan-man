# Health review — guide for interactive Claude Code sessions

When plan-man sends **"Ziel-Check: Gespräch lohnt sich"** (or you tap *Gespräch führen* on
*Woche*), start Claude Code in `/opt/appl` and say **"health review"**. The session reads this
file and works through the steps below. It runs on the Pro subscription, not the API key.

## Rules for the session

1. **Read only, until you say yes.** Every query below is a `SELECT`. Nothing is changed in the
   database without an explicit "yes" to a concrete change.
2. **Propose, don't decide.** The session explains what the numbers show and proposes at most one
   change (calorie target ±200 kcal, never below 1 700; protein; target date). You accept it.
3. **Prefer the app for the change.** A proposal from the goal check is accepted on *Woche* →
   *Vorschlag übernehmen…*, which opens the goal form prefilled
   (`https://plan-man.altenburger.io/health/goal?review=<id>`). For a change the session worked
   out itself, open *Ziel & Verlauf* → *Ziel ändern* and type the values. Only if you ask for it,
   the session writes the change itself (SQL at the end).
4. Weight is judged by the 7-day trend only. Missing days mean "not logged", not "ate nothing".
   Paused days (`health_pause`) never count as missed.

## Connecting

```bash
P=$(grep ^SPRING_DATASOURCE_PASSWORD /opt/appl/plan-man/.env | cut -d= -f2-)
docker exec -i -e MYSQL_PWD="$P" mariadb mariadb -uplan-man plan-man
```

The user is `michael` (`user_profile.username`). Health data belongs to this one login.

## Tables

| Table | Holds |
|---|---|
| `health_goal` | one row per goal; `valid_from` / `valid_to` (exclusive; NULL = current), `kcal_target`, `protein_target_g`, `football_bonus_kcal`, `steps_target`, `start_weight_kg`, `target_weight_kg`, `target_date`, `push_ups_target` |
| `health_day` | per-day snapshot of the targets in force (`kcal_target`, `protein_target_g`), `paused`, `football_cancelled` |
| `health_pause` | `start_date`, `end_date` (inclusive; NULL = running), `reason` |
| `meal`, `meal_item` | logged meals (`eaten_on`, `slot`, `kcal`, `protein_g`, …) and their items |
| `workout` | `done_on`, `type` (FREELETICS / FOOTBALL / WALK / CYCLING / OTHER), `duration_min`, `intensity`, `bonus_kcal` (what it added to the day's target) |
| `body_measurement` | `measured_on`, `type` (WEIGHT / WAIST / PUSH_UPS), `measured_value` |
| `weekly_review` | one row per week (`week_start` = Monday): the week's numbers, Opus' `headline` / `feedback`, and every 4 weeks `goal_check`, `goal_status` (ON_TRACK / RECOMPOSITION / TOO_FAST / STALLED), `goal_reason`, the proposal (`proposed_kcal_target`, `proposed_protein_target_g`, `proposed_target_date`, `proposal_reason`) and your `decision` (TALK / KEEP / ACCEPTED) |

## Queries

Current goal and history:

```sql
SELECT id, title, valid_from, valid_to, kcal_target, protein_target_g, football_bonus_kcal,
       start_weight_kg, target_weight_kg, target_date, push_ups_target
FROM health_goal WHERE user_profile_id = 'michael' ORDER BY valid_from DESC, id DESC;
```

Last 12 weekly reviews (the long-term record):

```sql
SELECT week_start, logged_days, paused_days, kcal_avg, kcal_target_avg, protein_avg_g, protein_days,
       freeletics_sessions, football_played, weight_trend_kg, weight_change_kg, weekly_rate_kg,
       waist_cm, waist_change_cm, push_ups, goal_check, goal_status, decision
FROM weekly_review WHERE user_profile_id = 'michael' ORDER BY week_start DESC LIMIT 12;

SELECT week_start, headline, feedback, goal_reason, proposal_reason
FROM weekly_review WHERE user_profile_id = 'michael' ORDER BY week_start DESC LIMIT 4;
```

Days of the last 4 weeks (change `28` to `84` for 12 weeks):

```sql
SELECT d.day, COALESCE(m.kcal, 0) AS kcal, COALESCE(m.protein, 0) AS protein_g, m.meals,
       w.bonus AS training_kcal, w.types, bm.measured_value AS weight
FROM (SELECT CURDATE() - INTERVAL n DAY AS day
      FROM (SELECT a.n + b.n * 10 AS n FROM
              (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
               UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a,
              (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
               UNION SELECT 6 UNION SELECT 7 UNION SELECT 8) b) nums
      WHERE n < 28) d
LEFT JOIN (SELECT eaten_on, SUM(kcal) kcal, ROUND(SUM(protein_g)) protein, COUNT(*) meals
           FROM meal WHERE user_profile_id = 'michael' GROUP BY eaten_on) m ON m.eaten_on = d.day
LEFT JOIN (SELECT done_on, SUM(bonus_kcal) bonus, GROUP_CONCAT(type) types
           FROM workout WHERE user_profile_id = 'michael' GROUP BY done_on) w ON w.done_on = d.day
LEFT JOIN body_measurement bm ON bm.user_profile_id = 'michael' AND bm.type = 'WEIGHT' AND bm.measured_on = d.day
ORDER BY d.day;
```

Waist, push-ups and pauses:

```sql
SELECT measured_on, type, measured_value FROM body_measurement
WHERE user_profile_id = 'michael' AND type IN ('WAIST', 'PUSH_UPS') ORDER BY measured_on;

SELECT start_date, end_date, reason FROM health_pause WHERE user_profile_id = 'michael' ORDER BY start_date;
```

## How to judge (same rules as the automatic goal check)

- **Rate** = slope of the weight over 3 weeks (`weekly_rate_kg`). Needed rate =
  (target − current trend) ÷ weeks left.
- **Too fast:** faster than −0.8 kg/week or more than twice the needed rate → risk of losing
  muscle; raise calories by 100–200.
- **Stalled:** trend flat (−0.1 to +0.1 kg/week) for 3+ weeks while losing is the goal and the
  waist is not going down. First check logging: few logged days, or many restaurant meals, explain
  more than a wrong target. Then lower calories by 100–200 (never below 1 700) or move the target
  date.
- **Recomposition:** weight flat but waist down ≥ 1 cm since the goal began → keep going.
- Daily burn was estimated at ~2 300–2 450 kcal. After 3+ weeks of good logging it can be
  checked: average intake + (trend change in kg × 7 700 ÷ days) ≈ real daily burn.

## Closing the session

- If you accept a change: do it in the app (rule 3). Accepting the proposal from *Woche* also
  sets `weekly_review.decision = 'ACCEPTED'`.
- If nothing changes: tap *Passt so* on *Woche* (sets `decision = 'KEEP'`).

Only if you explicitly ask the session to write the change, it runs (one transaction, after
showing you the values):

```sql
START TRANSACTION;
UPDATE health_goal SET valid_to = CURDATE()
 WHERE user_profile_id = 'michael' AND valid_to IS NULL;
INSERT INTO health_goal (user_profile_id, title, valid_from, kcal_target, protein_target_g,
       football_bonus_kcal, steps_target, start_weight_kg, target_weight_kg, target_date,
       push_ups_target, created_at)
SELECT user_profile_id, title, CURDATE(), <kcal>, <protein>, football_bonus_kcal, steps_target,
       start_weight_kg, target_weight_kg, <'YYYY-MM-DD' or target_date>, push_ups_target, NOW()
FROM health_goal WHERE id = <current goal id>;
COMMIT;
```

A goal changed on the same day twice leaves a zero-length row; that is harmless (it never applies
to a day). Today's `health_day` row keeps the old targets until the app touches the day again;
saving any meal or weight refreshes it only when the row has no goal, so prefer the app.
