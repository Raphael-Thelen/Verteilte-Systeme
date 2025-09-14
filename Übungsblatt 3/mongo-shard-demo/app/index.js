require("dotenv").config();
const express = require("express");
const { MongoClient } = require("mongodb");

const app = express();
app.use(express.json());

// Env-Variablen auslesen
const uri =
  process.env.MONGO_URI ||
  "mongodb://localhost:27017/?readPreference=primaryPreferred";
const port = process.env.PORT || 3000;
const rawW = process.env.WRITE_CONCERN_W || "1";
const WRITE_CONCERN_W = rawW === "majority" ? "majority" : parseInt(rawW, 10);
const WRITE_CONCERN_J = process.env.WRITE_CONCERN_J === "true";
const READ_CONCERN_LVL = process.env.READ_CONCERN_LEVEL || "local";
const READ_PREFERENCE = process.env.READ_PREFERENCE || "primaryPreferred";

async function main() {
  // Verbindung aufbauen
  const client = new MongoClient(uri);
  await client.connect();
  console.log("✅ Connected to MongoDB");

  const coll = client.db("testdb").collection("counters");

  // POST /increment
  app.post("/increment", async (req, res) => {
    const { counterId, delta } = req.body;
    if (typeof counterId !== "string" || typeof delta !== "number") {
      return res.status(400).json({ error: "Invalid payload" });
    }
    try {
      const result = await coll.findOneAndUpdate(
        { _id: counterId },
        { $inc: { value: delta, version: 1 } },
        {
          upsert: true,
          returnDocument: "after",
          writeConcern: { w: WRITE_CONCERN_W, j: WRITE_CONCERN_J },
          readConcern: { level: READ_CONCERN_LVL },
        }
      );
      res.json(result.value);
    } catch (err) {
      console.error("Increment error:", err);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /value
  app.get("/value", async (req, res) => {
    const counterId = req.query.counterId;
    if (!counterId) {
      return res.status(400).json({ error: "Missing counterId query param" });
    }
    try {
      const doc = await coll.findOne(
        { _id: counterId },
        {
          readConcern: { level: READ_CONCERN_LVL },
          readPreference: READ_PREFERENCE,
        }
      );
      if (!doc) {
        return res.json({ _id: counterId, value: 0, version: 0 });
      }
      res.json(doc);
    } catch (err) {
      console.error("Value error:", err);
      res.status(500).json({ error: err.message });
    }
  });

  // Server starten
  app.listen(port, () => {
    console.log(`🚀 App listening on port ${port}`);
  });
}

main().catch((err) => {
  console.error("❌ Failed to start app:", err);
  process.exit(1);
});
