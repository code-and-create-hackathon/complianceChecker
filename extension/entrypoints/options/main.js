// Options (settings) page. With a backend: login and the company policy. Without (demo mode): local rules upload.
import { BACKEND_ORIGIN } from '@shared/config.js';
import { initAccount } from './account.js';
import { initRules } from './rules.js';

if (BACKEND_ORIGIN) initAccount();
else initRules();
