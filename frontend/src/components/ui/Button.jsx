import React from 'react';
import { cn } from '../../lib/utils';

export const Button = ({
  children,
  className = '',
  variant = 'primary',
  size = 'md',
  isLoading = false,
  disabled = false,
  ...props
}) => {
  const baseStyles = 'inline-flex items-center justify-center font-medium rounded-sm transition-all duration-150 focus:outline-none focus:ring-1 focus:ring-[#1b1c19] disabled:opacity-50 disabled:cursor-not-allowed';

  const variants = {
    primary: 'bg-[#d1402a] hover:bg-[#ae2714] text-[#fffafa] shadow-[2px_2px_0px_0px_#1b1c19] active:translate-x-[1px] active:translate-y-[1px] font-mono uppercase tracking-wider',
    secondary: 'bg-[#efeee8] hover:bg-[#e4e2dd] text-[#1b1c19] border border-[#d1c9b8] shadow-sm font-mono uppercase tracking-wider',
    outline: 'border border-[#d1c9b8] hover:bg-[#efeee8] text-[#1b1c19]',
    ghost: 'hover:bg-[#efeee8] text-[#5a413c] hover:text-[#1b1c19]',
    danger: 'bg-[#ba1a1a] hover:bg-[#93000a] text-white shadow-[2px_2px_0px_0px_#1b1c19] font-mono uppercase tracking-wider',
    dark: 'bg-[#30312d] hover:bg-[#1b1c19] text-[#f2f1eb] shadow-[2px_2px_0px_0px_#1b1c19] font-mono uppercase tracking-wider',
  };

  const sizes = {
    sm: 'px-2.5 py-1 text-xs',
    md: 'px-4 py-2 text-sm',
    lg: 'px-6 py-3 text-base',
  };

  return (
    <button
      className={cn(baseStyles, variants[variant] || variants.primary, sizes[size] || sizes.md, className)}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading ? (
        <span className="flex items-center gap-2">
          <svg className="animate-spin h-3.5 w-3.5 text-current" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
          </svg>
          Loading...
        </span>
      ) : (
        children
      )}
    </button>
  );
};
