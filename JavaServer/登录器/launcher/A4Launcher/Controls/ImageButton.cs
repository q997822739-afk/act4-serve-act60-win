using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace A4Launcher.Controls;

/// <summary>
/// 三态图片按钮：正常 / 悬停 / 按下各一张图（素材来自 Zero 的 DNFLauncher，已按洋红底去背）。
/// 外观完全由图片决定，所以模板是空的，只显示一张 Image。
/// </summary>
public sealed class ImageButton : Button
{
    public static readonly DependencyProperty NormalImageProperty =
        DependencyProperty.Register(nameof(NormalImage), typeof(ImageSource), typeof(ImageButton));

    public static readonly DependencyProperty HoverImageProperty =
        DependencyProperty.Register(nameof(HoverImage), typeof(ImageSource), typeof(ImageButton));

    public static readonly DependencyProperty PressedImageProperty =
        DependencyProperty.Register(nameof(PressedImage), typeof(ImageSource), typeof(ImageButton));

    public ImageSource? NormalImage
    {
        get => (ImageSource?)GetValue(NormalImageProperty);
        set => SetValue(NormalImageProperty, value);
    }

    public ImageSource? HoverImage
    {
        get => (ImageSource?)GetValue(HoverImageProperty);
        set => SetValue(HoverImageProperty, value);
    }

    public ImageSource? PressedImage
    {
        get => (ImageSource?)GetValue(PressedImageProperty);
        set => SetValue(PressedImageProperty, value);
    }

    static ImageButton()
    {
        DefaultStyleKeyProperty.OverrideMetadata(typeof(ImageButton),
            new FrameworkPropertyMetadata(typeof(ImageButton)));
    }
}
