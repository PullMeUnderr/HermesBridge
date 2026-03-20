"use client";

import styles from "./MembersPanel.module.scss";
import { Avatar } from "@/components/ui/Avatar";
import { renderPresenceLabel } from "@/lib/format";
import type { ConversationMember } from "@/types/api";

interface MembersPanelProps {
  token: string;
  members: ConversationMember[];
  className?: string;
}

export function MembersPanel({ token, members, className }: MembersPanelProps) {
  return (
    <aside className={`${styles.panel} ${className ?? ""}`}>
      <div>
        <div className={styles.caption}>Состав</div>
        <h3>Участники</h3>
      </div>

      <div className={styles.list}>
        {members.map((member) => (
          <article key={member.userId} className={styles.row}>
            <Avatar token={token} name={member.displayName} src={member.avatarUrl} size="sm" />
            <div className={styles.copy}>
              <strong>{member.displayName}</strong>
              <span>@{member.username}</span>
              <span>{renderPresenceLabel(member)}</span>
            </div>
            <span className={styles.role}>{member.role}</span>
          </article>
        ))}
      </div>

      <div className={styles.note}>
        <strong>Инвайт</strong>
        <p>Создай код сверху и отправь его человеку. Вход доступен через бота или через форму слева.</p>
      </div>
    </aside>
  );
}
