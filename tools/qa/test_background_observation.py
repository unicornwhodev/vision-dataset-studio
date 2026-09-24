"""A visible launcher or an unknown OEM dump must not falsify background evidence."""
import unittest
from observe_background_training import APP, app_task_visibility


class BackgroundObservationTests(unittest.TestCase):
    def test_ignores_visible_launcher_and_similarly_named_app(self):
        dump = f'''* Task{{aaa A=10112:com.hihonor.android.launcher visible=true}}
* Task{{bbb A=10491:{APP}.releaseqa visible=true}}
* Task{{ccc A=10491:{APP} U=0 visible=false visibleRequested=false}}'''
        visible, tasks = app_task_visibility(dump)
        self.assertFalse(visible)
        self.assertEqual(1, len(tasks))

    def test_visible_or_requested_app_is_not_background(self):
        for flags in ('visible=true', 'visible=false visibleRequested=true'):
            with self.subTest(flags=flags):
                self.assertTrue(app_task_visibility(f'Task{{aaa A=10491:{APP} U=0 {flags}}}')[0])

    def test_any_visible_app_task_disqualifies_observation(self):
        dump = f'Task{{aaa A=10491:{APP} visible=false}}\nTask{{bbb A=10491:{APP} visible=true}}'
        self.assertTrue(app_task_visibility(dump)[0])

    def test_missing_or_unknown_app_visibility_fails_closed(self):
        for dump in ('', 'Task{aaa A=10112:launcher visible=true}', f'Task{{aaa A=10491:{APP} U=0}}'):
            with self.subTest(dump=dump), self.assertRaises(RuntimeError):
                app_task_visibility(dump)


if __name__ == '__main__':
    unittest.main()
