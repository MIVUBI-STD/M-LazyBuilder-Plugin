import './styles/app.css';
import App from './App.svelte';
import { mount } from 'svelte';
import { installActivityAccessibility } from './app/activityAccessibility';
import { installDisclosureAccessibility } from './app/disclosureAccessibility';
import { installModalAccessibility } from './app/modalAccessibility';

mount(App, { target: document.getElementById('app')! });
installModalAccessibility();
installDisclosureAccessibility();
installActivityAccessibility();
