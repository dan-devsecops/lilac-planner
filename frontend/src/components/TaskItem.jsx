import { useEffect, useRef, useState } from 'react';

const RECURRENCE_LABELS = {
  NONE: null,
  DAILY: 'daily',
  WEEKLY: 'weekly',
  MONTHLY: 'monthly',
  YEARLY: 'yearly',
};

export default function TaskItem({
  task,
  onToggle,
  onPoints,
  onTitle,
  onDelete,
  onClearAlarm,
  onCopyToNextDay,
  onKeyboardMove,
  // drag-and-drop
  isDragging,
  dropEdge,            // 'before' | 'after' | null
  onDragStart,
  onDragOver,
  onDragLeave,
  onDrop,
  onDragEnd,
}) {
  const [points, setPoints] = useState(task.points);
  const [editingTitle, setEditingTitle] = useState(false);
  const [title, setTitle] = useState(task.title);
  const [copying, setCopying] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const titleInputRef = useRef(null);
  const confirmTimeoutRef = useRef(null);

  useEffect(() => { setPoints(task.points); }, [task.points]);
  useEffect(() => { setTitle(task.title); }, [task.title]);
  useEffect(() => () => clearTimeout(confirmTimeoutRef.current), []);
  useEffect(() => {
    if (editingTitle && titleInputRef.current) {
      titleInputRef.current.focus();
      titleInputRef.current.select();
    }
  }, [editingTitle]);

  const commitPoints = () => {
    const v = Math.max(0, Number.isFinite(+points) ? Math.floor(+points) : task.points);
    if (v !== task.points) onPoints(task.id, v);
    else setPoints(task.points);
  };

  const commitTitle = () => {
    const t = title.trim();
    setEditingTitle(false);
    if (t && t !== task.title) onTitle(task.id, t);
    else setTitle(task.title);
  };

  const handleCopyClick = async () => {
    if (!onCopyToNextDay || copying) return;
    setCopying(true);
    try { await onCopyToNextDay(task); }
    finally { setCopying(false); }
  };

  const handleDeleteClick = () => {
    if (!confirmDelete) {
      setConfirmDelete(true);
      confirmTimeoutRef.current = setTimeout(() => setConfirmDelete(false), 3000);
      return;
    }
    clearTimeout(confirmTimeoutRef.current);
    setConfirmDelete(false);
    onDelete(task.id);
  };

  const cancelConfirmDelete = () => {
    clearTimeout(confirmTimeoutRef.current);
    setConfirmDelete(false);
  };

  const recLabel = RECURRENCE_LABELS[task.recurrence];

  const className = [
    'task-item',
    task.completed ? 'completed' : '',
    isDragging ? 'dragging' : '',
    dropEdge === 'before' ? 'drop-before' : '',
    dropEdge === 'after' ? 'drop-after' : '',
  ].filter(Boolean).join(' ');

  return (
    <li
      className={className}
      draggable={!editingTitle}
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', String(task.id));
        onDragStart?.(task.id);
      }}
      onDragOver={(e) => {
        if (onDragOver) {
          e.preventDefault();
          e.dataTransfer.dropEffect = 'move';
          const rect = e.currentTarget.getBoundingClientRect();
          const before = e.clientY < rect.top + rect.height / 2;
          onDragOver(task.id, before);
        }
      }}
      onDragLeave={() => onDragLeave?.(task.id)}
      onDrop={(e) => {
        e.preventDefault();
        onDrop?.(task.id);
      }}
      onDragEnd={() => onDragEnd?.()}
    >
      <button
        type="button"
        className="drag-handle"
        aria-label={`Reorder "${task.title}" - press Up or Down arrow to move`}
        onKeyDown={(e) => {
          if (e.key === 'ArrowUp') { e.preventDefault(); onKeyboardMove?.('up'); }
          if (e.key === 'ArrowDown') { e.preventDefault(); onKeyboardMove?.('down'); }
        }}
      >⋮⋮</button>

      <input
        type="checkbox"
        className="checkbox"
        checked={task.completed}
        onChange={(e) => onToggle(task.id, e.target.checked)}
        aria-label={`Mark "${task.title}" complete`}
      />

      <div className="title-cell">
        {editingTitle ? (
          <input
            ref={titleInputRef}
            type="text"
            className="title-input"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={commitTitle}
            onKeyDown={(e) => {
              if (e.key === 'Enter') e.currentTarget.blur();
              if (e.key === 'Escape') { setTitle(task.title); setEditingTitle(false); }
            }}
            maxLength={200}
          />
        ) : (
          <span
            className="title"
            role="button"
            tabIndex={0}
            title="Click to edit"
            onClick={() => setEditingTitle(true)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setEditingTitle(true); }
            }}
          >{task.title}</span>
        )}
        <div className="task-meta">
          {task.scheduledTime && (
            <span className="badge time" title="Scheduled time">
              ⏰ {task.scheduledTime.slice(0, 5)}
            </span>
          )}
          {recLabel && (
            <span className="badge recurrence" title={`Recurs ${recLabel}`}>↻ {recLabel}</span>
          )}
        </div>
      </div>

      <div className="points-edit">
        <input
          type="number"
          min="0"
          step="1"
          value={points}
          onChange={(e) => setPoints(e.target.value)}
          onBlur={commitPoints}
          onKeyDown={(e) => { if (e.key === 'Enter') e.currentTarget.blur(); }}
          aria-label={`Points for "${task.title}"`}
        />
        <span className="pts-label">pts</span>
      </div>

      <button
        type="button"
        className="icon copy-icon"
        title="Copy to next day"
        onClick={handleCopyClick}
        disabled={copying}
        aria-label={`Copy "${task.title}" to next day`}
      >→</button>

      <button
        type="button"
        className={`icon danger-icon${confirmDelete ? ' confirm-active' : ''}`}
        title={confirmDelete ? 'Click again to confirm delete' : 'Delete task'}
        onClick={handleDeleteClick}
        onBlur={cancelConfirmDelete}
        aria-label={confirmDelete ? `Confirm delete "${task.title}"` : `Delete "${task.title}"`}
      >{confirmDelete ? '?' : '×'}</button>
    </li>
  );
}
