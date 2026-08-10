import { useEffect, useState } from 'react';
import { api } from '../api.js';
import { thresholdFor, nextThreshold } from '../stickers.js';

export default function StickerShelf({ earned, totalPoints }) {
  const [catalog, setCatalog] = useState({});
  const [catalogError, setCatalogError] = useState(false);

  useEffect(() => {
    api.getStickers()
      .then((list) => {
        const map = {};
        list.forEach((s) => { map[s.code] = s; });
        setCatalog(map);
      })
      .catch(() => { setCatalog({}); setCatalogError(true); });
  }, []);

  const next = nextThreshold(totalPoints);
  const remaining = next - totalPoints;

  return (
    <section className="sticker-shelf">
      <h3>✨ Sticker Collection</h3>
      {catalogError && (
        <div className="sticker-error">
          Could not load sticker catalog.
        </div>
      )}
      {earned.length === 0 ? (
        <div className="sticker-hint">
          Earn your first sticker at <b>20 points</b> - {remaining} to go!
        </div>
      ) : (
        <div className="sticker-grid">
          {earned.map((code, i) => {
            const s = catalog[code];
            return (
              <div key={code} className="sticker" title={s ? `${s.name} (${thresholdFor(i)} pts)` : code}>
                <span>{s ? s.emoji : '🎉'}</span>
                <span className="threshold">{thresholdFor(i)}</span>
              </div>
            );
          })}
          <div className="sticker placeholder" title={`${remaining} pts until next sticker`}>
            +{remaining}
          </div>
        </div>
      )}
    </section>
  );
}
