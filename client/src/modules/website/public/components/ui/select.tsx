import { ChevronDown } from 'lucide-react';

export function Select({ value, onValueChange, label, options }: {
  value: string; onValueChange: (value: string) => void; label: string;
  options: { value: string; label: string }[];
}) {
  return <div className="public-select">
    <select value={value} onChange={event => onValueChange(event.target.value)} className="select-trigger" aria-label={label}>
      {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
    </select>
    <ChevronDown size={16} aria-hidden="true" />
  </div>;
}
