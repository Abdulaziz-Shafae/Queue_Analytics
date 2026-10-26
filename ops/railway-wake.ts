const hour = Number(new Intl.DateTimeFormat("en-GB", {timeZone:"Asia/Riyadh", hour:"2-digit", hourCycle:"h23"}).format(new Date()));
const weekday = new Intl.DateTimeFormat("en-US", {timeZone:"Asia/Riyadh",weekday:"short"}).format(new Date());
if (hour >= 16 && weekday !== "Mon" && weekday !== "Tue") {
  for (let attempt=0; attempt<3; attempt++) {
    try {
      const response = await fetch("https://queueanalytics-production.up.railway.app/actuator/health", {signal:AbortSignal.timeout(90000)});
      if (!response.ok) throw new Error("Health HTTP "+response.status);
      console.log("Analytics awake: HTTP "+response.status);
      break;
    } catch (error) {
      if (attempt===2) throw error;
      await Bun.sleep(10000);
    }
  }
} else { console.log("Outside collection window; no wake request."); }
