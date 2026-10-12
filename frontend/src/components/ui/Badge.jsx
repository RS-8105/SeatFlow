import React from 'react';
import { cn } from '../../lib/utils';

export const Badge = ({
  children,
  className = '',
  variant = 'default',
  ...props
}) => {
  const base = 'inline-flex items-center px-2 py-0.5 rounded-sm font-mono text-[10px] uppercase font-semibold tracking-wider';

  const variants = {
    default: 'bg-[#efeee8] text-[#1b1c19] border border-[#d1c9b8]',
    success: 'bg-[#d8e5be] text-[#404a2e] border border-[#576244]/30',
    warning: 'bg-[#ffdea4] text-[#775600] border border-[#f0bf5c]',
    danger: 'bg-[#ffdad6] text-[#93000a] border border-[#ba1a1a]/30',
    outline: 'text-[#5a413c] border border-[#d1c9b8]',
  };

  return (
    <span className={cn(base, variants[variant] || variants.default, className)} {...props}>
      {children}
    </span>
  );
};
