import { useEffect, useMemo, useState } from 'react';
import {
  CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import {
  eachDayOfInterval, endOfMonth, endOfQuarter, endOfWeek, endOfYear,
  format, parseISO, startOfMonth, startOfQuarter, startOfWeek, startOfYear,
} from 'date-fns';
import { api } from '../api.js';
import { useTheme } from '../theme.js';

// Recharts takes literal colors (SVG attributes can't resolve CSS variables),
// so the palette is mirrored here per theme.
const CHART_COLORS = {
  light: {
    points: '#9333EA', completed: '#C084FC', grid: '#E9D5FF', tick: '#6f5f93',
    tooltipBg: '#ffffff', tooltipBorder: '#E9D5FF', tooltipLabel: '#7E22CE',
  },
  dark: {
    points: '#C084FC', completed: '#8b6fd0', grid: '#352a59', tick: '#a797cf',
    tooltipBg: '#1f1837', tooltipBorder: '#4c3d7c', tooltipLabel: '#D8B4FE',
  },
};

const RANGES = [
  { key: 'week',    label: 'Week',    start: (d) => startOfWeek(d, { weekStartsOn: 1 }), end: (d) => endOfWeek(d, { weekStartsOn: 1 }) },
  { key: 'month',   label: 'Month',   start: startOfMonth,   end: endOfMonth },
  { key: 'quarter', label: 'Quarter', start: startOfQuarter, end: endOfQuarter },
  { key: 'year',    label: 'Year',    start: startOfYear,    end: endOfYear },
];

export default function Statistics() {
  const colors = CHART_COLORS[useTheme()];
  const [range, setRange] = useState('week');
  const [points, setPoints] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const cfg = RANGES.find((r) => r.key === range);
  const today = new Date();
  const from = cfg.start(today);
  const to = cfg.end(today);
  const fromStr = format(from, 'yyyy-MM-dd');
  const toStr = format(to, 'yyyy-MM-dd');

  useEffect(() => {
    setLoading(true);
    api.getStats(fromStr, toStr)
      .then((data) => { setPoints(data); setError(null); })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [fromStr, toStr]);

  const chartData = useMemo(() => {
    const start = parseISO(fromStr);
    const end = parseISO(toStr);
    const byDate = new Map(points.map((p) => [p.date, p]));
    return eachDayOfInterval({ start, end }).map((d) => {
      const k = format(d, 'yyyy-MM-dd');
      const p = byDate.get(k);
      return {
        date: k,
        label: format(d, range === 'year' || range === 'quarter' ? 'MMM d' : 'EEE d'),
        points: p ? p.points : 0,
        completed: p ? p.completedTasks : 0,
        total: p ? p.totalTasks : 0,
      };
    });
  }, [points, range, fromStr, toStr]);

  const summary = useMemo(() => {
    const totalPoints = chartData.reduce((s, x) => s + x.points, 0);
    const completed = chartData.reduce((s, x) => s + x.completed, 0);
    const totalTasks = chartData.reduce((s, x) => s + x.total, 0);
    const activeDays = chartData.filter((x) => x.points > 0 || x.total > 0).length;
    const avg = activeDays === 0 ? 0 : (totalPoints / activeDays).toFixed(1);
    return { totalPoints, completed, totalTasks, avg, activeDays };
  }, [chartData]);

  return (
    <>
      <div className="stats-controls">
        {RANGES.map((r) => (
          <button
            key={r.key}
            className={`range-btn ${range === r.key ? 'active' : ''}`}
            onClick={() => setRange(r.key)}
          >{r.label}</button>
        ))}
      </div>

      <div className="card">
        <div className="stats-header">
          <strong>
            {format(from, 'MMM d, yyyy')} - {format(to, 'MMM d, yyyy')}
          </strong>
          {loading && <span className="stats-loading">Loading…</span>}
        </div>

        {error && (
          <div className="error-banner">⚠ {error}</div>
        )}

        <div className="stats-summary">
          <div className="stat-tile">
            <div className="stat-num">{summary.totalPoints}</div>
            <div className="stat-label">Total points</div>
          </div>
          <div className="stat-tile">
            <div className="stat-num">{summary.completed}/{summary.totalTasks}</div>
            <div className="stat-label">Tasks completed</div>
          </div>
          <div className="stat-tile">
            <div className="stat-num">{summary.avg}</div>
            <div className="stat-label">Avg points / active day</div>
          </div>
        </div>

        <div style={{ width: '100%', height: 320 }}>
          <ResponsiveContainer>
            <LineChart data={chartData} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
              <XAxis dataKey="label" tick={{ fill: colors.tick, fontSize: 12 }} interval="preserveStartEnd" />
              <YAxis allowDecimals={false} tick={{ fill: colors.tick, fontSize: 12 }} />
              <Tooltip
                contentStyle={{ background: colors.tooltipBg, border: `1px solid ${colors.tooltipBorder}`, borderRadius: 10 }}
                labelStyle={{ color: colors.tooltipLabel, fontWeight: 700 }}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="points"
                name="Points"
                stroke={colors.points}
                strokeWidth={2.5}
                dot={{ r: 3, fill: colors.points }}
                activeDot={{ r: 6 }}
              />
              <Line
                type="monotone"
                dataKey="completed"
                name="Completed tasks"
                stroke={colors.completed}
                strokeWidth={2}
                strokeDasharray="4 4"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </>
  );
}
