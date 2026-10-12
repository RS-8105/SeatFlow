import React from 'react';
import { cn } from '../../lib/utils';

export const Card = ({
  className = '',
  children,
  ...props
}) => {
  return (
    <div
      className={cn(
        'bg-[#ffffff] border border-[#d1c9b8] rounded-sm p-5 shadow-sm text-[#1b1c19]',
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};
