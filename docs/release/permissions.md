# Permissões, elegibilidade e revisão

Rechecado em29/09/2026. Estado Console pendente (responsável ainda não definiu conta). Documentação primária: [target API](https://developer.android.com/google/play/requirements/target-sdk), [FGS/FSI](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en), [conteúdo de saúde](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).

| Código/manifesto | Uso | Acesso efetivo / evidência |
|---|---|---|
| POST_NOTIFICATIONS | Publicar lembrete | Verificar API33+ e canal; negativa sem sucesso falso |
| SCHEDULE_EXACT_ALARM | Agendar ocorrência em horário calculado | canScheduleExactAlarms API31+; SecurityException entre check e chamada; estado inexato explícito |
| USE_FULL_SCREEN_INTENT | Card por notificação | canUseFullScreenIntent API34+; usuário/sistema podem negar; prioridade/canal também determinam comportamento |
| FOREGROUND_SERVICE + MEDIA_PLAYBACK | Som finito de alarme | Iniciado em entrega exata permitida; falha capturada; fallback inexato não presume concessão para iniciar FGS |
| VIBRATE / WAKE_LOCK | Vibração e entrega finita | Ciclo serviço; liberar stop/destroy |
| RECEIVE_BOOT_COMPLETED | Reconciliar eventos | Após primeiro desbloqueio; não iniciar mediaPlayback pelo boot |

USE_EXACT_ALARM não mantida; portanto não se presume elegibilidade para a permissão restrita. Overlay, pedido de exclusão de bateria e WRITE_EXTERNAL_STORAGE removidos. PDF usa SAF. As permissões normais do código não equivalem a aprovação do Console. O responsável deve preencher declarações de FSI/FGS quando solicitadas, demonstrando lembrete real, som e ação do usuário com vídeo efetivamente gravado em E07; nenhum vídeo é alegado produzido antes de existir.

API36 é o mínimo de submissão confirmado desde31/08/2026; min26 permanece. Android17/API37 aparece na documentação atual e deverá ser incluído em validação de compatibilidade adicional. Inventário16KB deve inspecionar o APK/AAB real, sem deduzir ausência de .so por linguagem Kotlin.
