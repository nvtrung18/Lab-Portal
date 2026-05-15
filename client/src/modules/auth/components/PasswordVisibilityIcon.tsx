interface PasswordVisibilityIconProps {
  visible: boolean;
  className?: string;
}

export function PasswordVisibilityIcon({
  visible,
  className = 'h-5 w-5',
}: PasswordVisibilityIconProps) {
  if (visible) {
    return (
      <svg
        aria-hidden="true"
        className={className}
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
        viewBox="0 0 24 24"
      >
        <path d="m2 2 20 20" />
        <path d="M6.7 6.7C3.9 8.5 2 12 2 12s3.6 7 10 7c1.8 0 3.3-.5 4.6-1.2" />
        <path d="M19.3 15.3C21 13.6 22 12 22 12s-3.6-7-10-7c-.9 0-1.7.1-2.5.3" />
        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
      </svg>
    );
  }

  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="2"
      viewBox="0 0 24 24"
    >
      <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}
