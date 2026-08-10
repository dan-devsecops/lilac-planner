import { addDays, format, parseISO } from 'date-fns';
import { useNavigate } from 'react-router-dom';

export default function DateNav({ date }) {
  const navigate = useNavigate();
  const current = parseISO(date);
  const prettyLong = format(current, 'EEEE, MMMM d, yyyy');

  const go = (d) => navigate(`/day/${format(d, 'yyyy-MM-dd')}`);

  return (
    <div className="date-nav">
      <button className="ghost" onClick={() => go(addDays(current, -1))}>← Prev</button>
      <div className="date-nav-center">
        <div className="date-label">
          {prettyLong}
        </div>
        <input
          type="date"
          value={date}
          onChange={(e) => navigate(`/day/${e.target.value}`)}
          aria-label="Jump to date"
        />
      </div>
      <div className="date-nav-side">
        <button className="ghost" onClick={() => go(new Date())}>Today</button>
        <button className="ghost" onClick={() => go(addDays(current, 1))}>Next →</button>
      </div>
    </div>
  );
}
